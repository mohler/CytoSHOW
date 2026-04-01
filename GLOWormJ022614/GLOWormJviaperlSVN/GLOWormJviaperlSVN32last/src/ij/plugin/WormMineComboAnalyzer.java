package ij.plugin;

import ij.IJ;
import ij.plugin.PlugIn;

import java.io.BufferedReader;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;



public class WormMineComboAnalyzer implements PlugIn {

	private List<String> sortedPartsList = new ArrayList<String>();

    public List<String> getSortedPartsList() {
		return sortedPartsList;
	}

    @Override
    public void run(String arg) {
        // Parsing the text list a user drops onto the CytoSHOW canvas
        // Currently a hardcoded list from menu command, will be drag-and-drop payload later
        List<String> droppedGenes = Arrays.asList(arg.split("[;, ]+"));
        Path tempCsv = Paths.get("genes_" + arg.replaceAll("[;, ]+", "_").replaceAll("\\*", "") + "_to_parts_combo_data.csv");

        // The master method your UI listener (or IJ.Props) will call
        Map<Set<String>, Set<String>> preCalculatedData = analyzeAndGroup(droppedGenes, tempCsv);

        // Printing the results to IJ.log to verify the background processing
        IJ.log("--- Pre-Calculated Expression Combinations ---");
        for (Map.Entry<Set<String>, Set<String>> entry : preCalculatedData.entrySet()) {
//            IJ.log("Combo " + entry.getKey() + " -> " + entry.getValue().size() + " co-expressing Parts ["+ entry.getValue().toString()+"]");

        	// Stream and sort the Sets alphabetically right before displaying them
            List<String> sortedGenes = entry.getKey().stream()
                                            .sorted()
                                            .collect(Collectors.toList());
                                            
            List<String> sortedParts = entry.getValue().stream()
                                            .sorted()
                                            .collect(Collectors.toList());
            
            IJ.log("Combo " + sortedGenes + " -> " + sortedParts.size() + " expressing Cells/Parts "+ sortedParts +"");
            
            sortedPartsList.addAll(sortedParts);
            
           
        
        }
    }

    /**
     * Master method to be called by your CytoSHOW drag-and-drop listener.
     * Returns a Map where the Key is a combination of genes, and the Value is the Set of matching Parts.
     */
    public static Map<Set<String>, Set<String>> analyzeAndGroup(List<String> genesToSearch, Path outputPath) {
        Map<Set<String>, Set<String>> finalResults = new HashMap<>();

        // Quick safety check in case the regex split leaves us with an empty list
        if (genesToSearch == null || genesToSearch.isEmpty() || (genesToSearch.size() == 1 && genesToSearch.get(0).trim().isEmpty())) {
            IJ.log("No valid genes provided in the argument.");
            return finalResults;
        }

        boolean downloadSuccess = downloadGeneExpressionData(genesToSearch, outputPath);
        if (!downloadSuccess) {
            return finalResults;
        }

        // 1. Parse the CSV into a mapping of Part -> All Expressed Genes
        Map<String, Set<String>> partToGenesMap = parseCsv(outputPath);

        // 2. Generate the combinations to test
        List<Set<String>> combosToTest = new ArrayList<>();
        
        if (genesToSearch.size() <= MAX_GENES_FOR_COMBOS) {
            combosToTest = generateBitwiseCombinations(genesToSearch);
        } else {
            IJ.log("List exceeds combination limit (" + MAX_GENES_FOR_COMBOS + "). Only calculating the full list.");
            combosToTest.add(new HashSet<>(genesToSearch));
        }

        // 3. Cross-reference every combination against the parsed Part data
        for (Set<String> combo : combosToTest) {
            Set<String> matchingParts = new HashSet<>();
            
            for (Map.Entry<String, Set<String>> entry : partToGenesMap.entrySet()) {
                if (entry.getValue().containsAll(combo)) {
                    matchingParts.add(entry.getKey());
                }
            }
            
            // Only store the combination if it actually has overlapping anatomical parts
            if (!matchingParts.isEmpty()) {
                finalResults.put(combo, matchingParts);
            }
        }

        return finalResults;
    }

    /**
     * Highly efficient Java 8 bitwise logic to generate all subset combinations of size 2 or greater.
     */
    private static List<Set<String>> generateBitwiseCombinations(List<String> items) {
        List<Set<String>> allCombos = new ArrayList<>();
        int n = items.size();
        int max = 1 << n; // 2^n

        // Start at 1 to skip the empty set
        for (int i = 1; i < max; i++) {
            Set<String> currentCombo = new HashSet<>();
            for (int j = 0; j < n; j++) {
                // If the j-th bit is set in the current number i, include the j-th item
                if ((i & (1 << j)) != 0) {
                    currentCombo.add(items.get(j));
                }
            }
            // We only care about pairs or higher order combinations (or singletons if n=1)
            if (currentCombo.size() >= 2 || n == 1) {
                allCombos.add(currentCombo);
            }
        }
        return allCombos;
    }

    private static Map<String, Set<String>> parseCsv(Path csvPath) {
        Map<String, Set<String>> map = new HashMap<>();
        try (BufferedReader br = Files.newBufferedReader(csvPath)) {
            String line = br.readLine(); // Skip header
            while ((line = br.readLine()) != null) {
                String[] cols = line.split(",");
                if (cols.length >= 3) {
                    String partName = cols[0].replace("\"", "").trim();
                    String geneSymbol = cols[2].replace("\"", "").trim();
                    if (!partName.isEmpty()) {
                        map.computeIfAbsent(partName, k -> new HashSet<>()).add(geneSymbol);
                    }
                }
            }
        } catch (Exception e) {
            IJ.log("Error parsing CSV: " + e.getMessage());
        }
        return map;
    }

    private static boolean downloadGeneExpressionData(List<String> geneNames, Path outputPath) {
        StringBuilder logicBuilder = new StringBuilder();
        StringBuilder constraintsBuilder = new StringBuilder();

        // Ensure we don't try to build a query for empty strings
        List<String> cleanGenes = new ArrayList<>();
        for (String g : geneNames) {
            if (!g.trim().isEmpty()) {
                cleanGenes.add(g.trim());
            }
        }

        for (int i = 0; i < cleanGenes.size(); i++) {
            String code = String.valueOf((char) ('A' + i));
            
            if (i > 0) {
                logicBuilder.append(" OR ");
            }
            logicBuilder.append(code);

            String gene = cleanGenes.get(i);
            String op = gene.contains("*") ? "LIKE" : "=";

            constraintsBuilder.append("<constraint path=\"Gene.symbol\" ")
                              .append("op=\"").append(op).append("\" ")
                              .append("value=\"").append(gene).append("\" ")
                              .append("code=\"").append(code).append("\"/>");
        }

        String xml = "<query name=\"\" model=\"genomic\" view=\"Gene.expressionPatterns.anatomyTerms.name Gene.expressionPatterns.anatomyTerms.synonym Gene.symbol Gene.secondaryIdentifier\" "
                   + "longDescription=\"Return a list of parts that express specific genes.\" "
                   + "sortOrder=\"Gene.expressionPatterns.anatomyTerms.name asc\" "
                   + "constraintLogic=\"" + logicBuilder.toString() + "\">" 
                   + constraintsBuilder.toString() 
                   + "</query>";

        try {
            IJ.log("Fetching broad expression data from WormMine...");
            String encodedXml = URLEncoder.encode(xml, "UTF-8").replace("+", "%20");
            String urlString = "https://wormmine.alliancegenome.org/wormmine/service/query/results?query=" 
                             + encodedXml + "&format=csv&columnheaders=true";

            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (InputStream in = connection.getInputStream()) {
                    Files.copy(in, outputPath, StandardCopyOption.REPLACE_EXISTING);
                    return true;
                }
            } else {
                IJ.log("Download failed. HTTP: " + connection.getResponseCode());
            }
        } catch (Exception e) {
            IJ.log("Error during download: " + e.getMessage());
        }
        return false;
    }
    
	public void setSortedPartsList(List<String> sortedPartsList) {
		this.sortedPartsList = sortedPartsList;
	}

	// Safety guard to prevent 2^N memory explosions if a user drops too much text
    private static final int MAX_GENES_FOR_COMBOS = 12;

}

