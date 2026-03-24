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

public class WormMineAnatomyNamesToGenesExpressed implements PlugIn {

	
	public void run(String arg) {
		String[] args = arg.split("[,; ]");
		List<String> partsToSearch = Arrays.asList(args);	
		
		String urlString = buildWormMinePartsToGenesUrl(partsToSearch);
		IJ.log(urlString);
        Path outputPath = Paths.get("anatomy_parts_"+arg.replaceAll("[;, ]", "_").replaceAll("\\*","")+"_to_expr_genes_sorted.csv");
        IJ.log(outputPath.toString());
    
	        try {
	            IJ.log("Downloading data from WormMine using Java 8...");
	            
	            URL url = new URL(urlString);
	            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
	            
	            // Explicitly setting the request method to GET
	            connection.setRequestMethod("GET");
	            
	            int responseCode = connection.getResponseCode();
	            
	            if (responseCode == HttpURLConnection.HTTP_OK) {
	                // Using try-with-resources to ensure the InputStream closes automatically
	                try (InputStream inputStream = connection.getInputStream()) {
	                    // Files.copy streams the data directly to disk, replacing any existing file
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
	}
	
	private static String buildWormMinePartsToGenesUrl(List<String> partNames) {
        StringBuilder logicBuilder = new StringBuilder();
        StringBuilder constraintsBuilder = new StringBuilder();

        for (int i = 0; i < partNames.size(); i++) {
        	// Generate standard A, B, C codes. (Note: Supports up to 26 items before hitting non-alpha chars)
            String code = String.valueOf((char) ('A' + i));
            
            if (i > 0) {
                logicBuilder.append(" OR ");
            }
            logicBuilder.append(code);

            String part = partNames.get(i);
            
            // Automatically switch to the LIKE operator if a wildcard is detected
            String op = part.contains("*") ? "LIKE" : "=";

            constraintsBuilder.append("<constraint path=\"Gene.expressionPatterns.anatomyTerms.name\" ")
                              .append("op=\"").append(op).append("\" ")
                              .append("value=\"").append(part).append("\" ")
                              .append("code=\"").append(code).append("\"/>");
        }

        String xml = "<query name=\"\" model=\"genomic\" view=\"Gene.symbol Gene.secondaryIdentifier Gene.expressionPatterns.primaryIdentifier Gene.expressionPatterns.anatomyTerms.name Gene.expressionPatterns.anatomyTerms.synonym\" longDescription=\"Return a list of genes that are expressed in a specific part or tissue.\" sortOrder=\"Gene.symbol asc\" constraintLogic=\"" + logicBuilder.toString() + "\">" 
                     + constraintsBuilder.toString() 
                     + "</query>";

        try {
            // URL-encode the entire XML string so the server can parse it safely
            String encodedXml = URLEncoder.encode(xml, "UTF-8");
            return "https://wormmine.alliancegenome.org/wormmine/service/query/results?query=" + encodedXml + "&format=csv&columnheaders=true";
        } catch (Exception e) {
            throw new RuntimeException("Failed to URL-encode the XML payload", e);
        }
    }

}
