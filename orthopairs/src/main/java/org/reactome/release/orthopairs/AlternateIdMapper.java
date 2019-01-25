package org.reactome.release.orthopairs;

import java.io.*;
import java.util.*;

public class AlternateIdMapper {

    // TODO: Make sure duplicate handling exists for alt IDs
    // TODO: Line count discrepancys
    public static Map<String, Set<String>> getAltIdMappingFile(Object speciesKey, String alternateIdFilename) throws IOException {

        File alternateIdFile = new File(alternateIdFilename);
        BufferedReader br = new BufferedReader(new FileReader(alternateIdFile));
        Map<String,Set<String>> altIdToEnsemblMap = new HashMap<>();
        if (speciesKey.equals("hsap")) {
            altIdToEnsemblMap = mapHumanAlternateIds(br);
        } else if (speciesKey.equals("mmus")) {
            altIdToEnsemblMap = mapMouseAlternateIds(br);
        } else if (speciesKey.equals("rnor")) {
            altIdToEnsemblMap = mapRatAlternateIds(br);
        } else if (speciesKey.equals("xtro")) {
           altIdToEnsemblMap = mapFrogAlternateIds(br);
        } else if (speciesKey.equals("drer")) {
            altIdToEnsemblMap = mapZebraFishAlternateIds(br);
        } else if (speciesKey.equals("scer")) {
            altIdToEnsemblMap = mapYeastAlternateIds(br);
        } else {
            System.out.println("Did not locate species");
        }

        return altIdToEnsemblMap;
    }

    private static Map<String, Set<String>> mapHumanAlternateIds(BufferedReader br) throws IOException {
        Map<String, Set<String>> altIdToEnsemblMap = new HashMap<>();
        String line;
        int altIdIndex = 0;
        int ensemblIdIndex = 0;
        while ((line = br.readLine()) != null) {
            String[] tabSplit = line.split("\t");

            if (!line.startsWith("HGNC")) {
                for (int i = 0; i < tabSplit.length; i++) {
                    if (tabSplit[i].equals("hgnc_id")) {
                        altIdIndex = i;
                    }
                    if (tabSplit[i].equals("ensembl_gene_id")) {
                        ensemblIdIndex = i;
                    }
                }
            } else {
                boolean ensemblIdColumnExists = true;
                if (tabSplit.length < (ensemblIdIndex + 1)) {
                    ensemblIdColumnExists = false;
                }
                if (ensemblIdColumnExists && !tabSplit[ensemblIdIndex].equals("")) {
                    String altId = tabSplit[altIdIndex];
                    String ensemblId = tabSplit[ensemblIdIndex];
                    Set<String> firstIdAdded = new HashSet<>(Arrays.asList(ensemblId));
                    altIdToEnsemblMap.put(altId, firstIdAdded);
                }
            }
        }

        return altIdToEnsemblMap;
    }

    private static Map<String, Set<String>> mapMouseAlternateIds(BufferedReader br) throws IOException {
        Map<String, Set<String>> altIdToEnsemblMap = new HashMap<>();
        String line;
        int altIdIndex = 0;
        int ensemblIdIndex = 0;
        while ((line = br.readLine()) != null) {
            String[] tabSplit = line.split("\t");
            if (!line.startsWith("MGI:")) {
                for (int i = 0; i < tabSplit.length; i++) {
                    if (tabSplit[i].equals("MGI Accession ID")) {
                        altIdIndex = i;
                    }
                    if (tabSplit[i].equals("Ensembl Gene ID")) {
                        ensemblIdIndex = i;
                    }
                }
            } else {
                String altId = tabSplit[altIdIndex];
                String ensemblId = tabSplit[ensemblIdIndex];
                if (!ensemblId.equals("null")) {
                    Set<String> firstIdAdded = new HashSet<>(Arrays.asList(ensemblId));
                    altIdToEnsemblMap.put(altId, firstIdAdded);
                }
            }
        }
        return altIdToEnsemblMap;
    }

    private static Map<String, Set<String>> mapRatAlternateIds(BufferedReader br) throws IOException {
        String line;
        Map<String, Set<String>> altIdToEnsemblMap = new HashMap<>();
        int altIdIndex = 0;
        int ensemblIdIndex = 0;
        int lineCount = 0;
        while ((line = br.readLine()) != null) {
            lineCount++;
            String[] tabSplit = line.split("\t");
            if (line.startsWith("GENE")) {

                for (int i = 0; i<tabSplit.length; i++) {
                    if (tabSplit[i].equals("GENE_RGD_ID")) {
                        altIdIndex = i;
                    }
                    if (tabSplit[i].equals("ENSEMBL_ID")) {
                        ensemblIdIndex = i;
                    }
                }
            } else if (!line.startsWith("#")) {
                boolean ensemblIdColumnExists = true;
                if (tabSplit.length < (ensemblIdIndex + 1)) {
                    ensemblIdColumnExists = false;
                }
                if (ensemblIdColumnExists && !tabSplit[ensemblIdIndex].equals("")) {
                    String altId = tabSplit[altIdIndex];
                    String[] ensemblIds = tabSplit[ensemblIdIndex].split(";");
                    for (int i = 0; i < ensemblIds.length; i++) {
                        String ensemblId = ensemblIds[i];
                        if (altIdToEnsemblMap.get(altId) == null) {
                            Set<String> firstIdAdded = new HashSet<>(Arrays.asList(ensemblId));
                            altIdToEnsemblMap.put(altId, firstIdAdded);
                        } else {
                            altIdToEnsemblMap.get(altId).add(ensemblId);
                        }
                    }
                }
            }
        }
        return altIdToEnsemblMap;
    }

    private static Map<String,Set<String>> mapFrogAlternateIds(BufferedReader br) throws IOException {

        String line;
        Map<String, Set<String>> altIdToEnsemblMap = new HashMap<>();
        while ((line = br.readLine()) != null) {
            String[] tabSplit = line.split("\t");
            String altId = "";
            String ensemblId = "";
            for (int i = 0; i < tabSplit.length; i++) {
                if (tabSplit[i].startsWith("XB-GENE")) {
                    altId = tabSplit[i];
                }
                if (tabSplit[i].startsWith("ENSXETG")) {
                    ensemblId = tabSplit[i];
                }
            }
            Set<String> firstIdAdded = new HashSet<>(Arrays.asList(ensemblId));
            altIdToEnsemblMap.put(altId, firstIdAdded);
        }
        return altIdToEnsemblMap;
    }

    private static Map<String, Set<String>> mapZebraFishAlternateIds(BufferedReader br) throws IOException {
        String line;
        Map<String, Set<String>> altIdToEnsemblMap = new HashMap<>();
        while ((line = br.readLine()) != null) {
            String[] tabSplit = line.split("\t");
            String altId = "";
            String ensemblId = "";
            for (int i = 0; i < tabSplit.length; i++) {
                if (tabSplit[i].startsWith("ZDB-GENE")) {
                    altId = tabSplit[i];
                }
                if (tabSplit[i].startsWith("ENSDARG")) {
                    ensemblId = tabSplit[i];
                }
            }
            Set<String> firstIdAdded = new HashSet<>(Arrays.asList(ensemblId));
            altIdToEnsemblMap.put(altId, firstIdAdded);
        }
        return altIdToEnsemblMap;
    }

    private static Map<String, Set<String>> mapYeastAlternateIds(BufferedReader br) throws IOException {
        String line;
        Map<String, Set<String>> altIdToEnsemblMap = new HashMap<>();
        while ((line = br.readLine()) != null) {
            String[] tabSplit = line.split("\t");
            String altId = tabSplit[0];
            String ensemblId = tabSplit[1];
            Set<String> firstIdAdded = new HashSet<>(Arrays.asList(ensemblId));
            altIdToEnsemblMap.put(altId, firstIdAdded);
        }
        return altIdToEnsemblMap;
    }
}
