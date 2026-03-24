package ij.plugin;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ij.IJ;

public class WormMineCrossReferencer implements PlugIn {

	@Override
	public void run(String arg) {// Ensure this matches the output file from our fetcher class
        Path csvPath = Paths.get("genes_"+arg.replaceAll("[;, ]", "_").replaceAll("\\*","")+"_to_parts_sorted_with_headers.csv");
        
		String[] args = arg.split("[,; ]");
		        
         // The specific genes you want to enforce the strict AND condition on
        List<String> requiredGenes = Arrays.asList(args);
        
        // Maps a Part Name to the unique set of Gene Symbols it expresses
        Map<String, Set<String>> partToGenesMap = new HashMap<>();

        try (BufferedReader br = Files.newBufferedReader(csvPath)) {
            // Read and discard the header line
            String line = br.readLine(); 
            if (line == null) {
                IJ.log("The file is empty.");
                return;
            }

            // Parse the CSV line by line
            while ((line = br.readLine()) != null) {
                // Split by comma. WormMine symbols and part names rarely contain internal commas,
                // making a simple split perfectly safe and fast.
                String[] columns = line.split(",");
                
                // Ensure the row has enough columns to grab the Part Name (Index 0) and Gene Symbol (Index 2)
                if (columns.length >= 3) {
                    String partName = columns[0].replace("\"", "").trim();
                    String geneSymbol = columns[2].replace("\"", "").trim();
                    
                    if (!partName.isEmpty()) {
                        // computeIfAbsent is a highly efficient Java 8 addition to maps
                        partToGenesMap.computeIfAbsent(partName, k -> new HashSet<>()).add(geneSymbol);
                    }
                }
            }

            IJ.log("Scanning for Parts expressing ALL of the following: " + requiredGenes);
            IJ.log("--------------------------------------------------");
            
            int matchCount = 0;
            // Iterate through our map to find the intersections
            for (Map.Entry<String, Set<String>> entry : partToGenesMap.entrySet()) {
                String part = entry.getKey();
                Set<String> expressedGenes = entry.getValue();
                
                // The magic AND filter: strictly checks if the Part's gene set contains the entire required list
                if (expressedGenes.containsAll(requiredGenes)) {
                    IJ.log("Match found: " + part + " (Expresses: " + expressedGenes + ")");
                    matchCount++;
                }
            }
            
            IJ.log("--------------------------------------------------");
            IJ.log("Total matching Parts found: " + matchCount);

        } catch (Exception e) {
            IJ.log("An error occurred while parsing the CSV: " + e.getMessage());
            e.printStackTrace();
        }}

}
