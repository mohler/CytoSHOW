package ij.plugin;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import ij.IJ;

public class WormMineGeneNamesToExpressingParts implements PlugIn {

	
	public void run(String arg) {
		String[] args = arg.split("[,; ]");
		List<String> genesToSearch = Arrays.asList(args);	
		        
        String urlString = buildWormMineGenesToPartsUrl(genesToSearch);
        Path outputPath = Paths.get("genes_"+arg.replaceAll("[;, ]", "_").replaceAll("\\*","")+"_to_parts_sorted_with_headers.csv");

        try {
            IJ.log("Downloading converse query (Genes to Parts) from WormMine...");
            
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            
            int responseCode = connection.getResponseCode();
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (InputStream inputStream = connection.getInputStream()) {
                    Files.copy(inputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
                    IJ.log("Success! File saved to: " + outputPath.toAbsolutePath());
                }
            } else {
                IJ.log("Failed to download. HTTP Status: " + responseCode);
            }
            
            connection.disconnect();
            
        } catch (Exception e) {
            IJ.log("An error occurred during the request: " + e.getMessage());
            e.printStackTrace();
        }	
        
        new WormMineCrossReferencer().run(arg);
	}
	
	private static String buildWormMineGenesToPartsUrl(List<String> geneNames) {
		StringBuilder logicBuilder = new StringBuilder();
        StringBuilder constraintsBuilder = new StringBuilder();

        for (int i = 0; i < geneNames.size(); i++) {
            // Generate standard A, B, C codes.
            String code = String.valueOf((char) ('A' + i));
            
            if (i > 0) {
                logicBuilder.append(" OR ");
            }
            logicBuilder.append(code);

            String gene = geneNames.get(i);
            
            // Automatically switch to the LIKE operator if a wildcard is detected
            String op = gene.contains("*") ? "LIKE" : "=";

            // Swapped constraint path to target the Gene symbol instead of the Part name
            constraintsBuilder.append("<constraint path=\"Gene.symbol\" ")
                              .append("op=\"").append(op).append("\" ")
                              .append("value=\"").append(gene).append("\" ")
                              .append("code=\"").append(code).append("\"/>");
        }

        // Swapped the view to put the Anatomy Term first, and changed the sortOrder to group by the Part name
        String xml = "<query name=\"\" model=\"genomic\" view=\"Gene.expressionPatterns.anatomyTerms.name Gene.expressionPatterns.anatomyTerms.synonym Gene.symbol Gene.secondaryIdentifier\" longDescription=\"Return a list of parts that express specific genes.\" sortOrder=\"Gene.expressionPatterns.anatomyTerms.name asc\" constraintLogic=\"" + logicBuilder.toString() + "\">" 
                     + constraintsBuilder.toString() 
                     + "</query>";

        try {
            String encodedXml = URLEncoder.encode(xml, "UTF-8").replace("+", "%20");
            return "https://wormmine.alliancegenome.org/wormmine/service/query/results?query=" + encodedXml + "&format=csv&columnheaders=true";
        } catch (Exception e) {
            throw new RuntimeException("Failed to URL-encode the XML payload", e);
        }
	}

}
