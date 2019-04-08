package org.reactome.release.generateGOAnnotationFile;

import org.gk.model.ClassAttributeFollowingInstruction;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaClass;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

public class Main {

    private static final String CELLULAR_COMPONENT = "Cellular Component";
    private static final String MOLECULAR_FUNCTION = "Molecular Function";
    private static final String BIOLOGICAL_PROCESS = "Biological Process";
    private static final List<String> goTerms = new ArrayList<>(Arrays.asList(CELLULAR_COMPONENT, MOLECULAR_FUNCTION, BIOLOGICAL_PROCESS));
    private static final String GOA_FILENAME = "gene_association.reactome";
    private static final int MAX_RECURSION_LEVEL = 2;
    private static final String uniprotDbString = "UniProtKB";
    private static final String PROTEIN_BINDING_ANNOTATION = "0005515";
    private static final List<String> speciesWithAlternateGOCompartment = new ArrayList<>(Arrays.asList("11676", "211044", "1491", "1392"));
    private static final List<String> microbialSpeciesToExclude = new ArrayList<>(Arrays.asList("813", "562", "491", "90371", "1280", "5811"));
    private static Set<String> goaLines = new HashSet<>();
    private static Map<String, Integer> dates = new HashMap<>();

    public static void main(String[] args) throws Exception {

        Properties props = new Properties();
        props.load(new FileInputStream("src/main/resources/config.properties"));

        String username = props.getProperty("username");
        String password = props.getProperty("password");
        String database = props.getProperty("database");
        String host = props.getProperty("host");
        int port = Integer.valueOf(props.getProperty("port"));

        MySQLAdaptor dbAdaptor = new MySQLAdaptor(host, database, username, password, port);

        for (GKInstance reactionInst : (Collection<GKInstance>) dbAdaptor.fetchInstancesByClass("ReactionlikeEvent")) {
            if (!isInferred(reactionInst)) {
                for (String goTerm : goTerms) {

                    Collection<GKInstance> catalystInstances = reactionInst.getAttributeValuesList(ReactomeJavaConstants.catalystActivity);

                    if (goTerm.equals(CELLULAR_COMPONENT) || (goTerm.equals(BIOLOGICAL_PROCESS) && catalystInstances.size() == 0)) {
                        Set<GKInstance> proteins = getReactionProteins(reactionInst);
                        processProteins(goTerm, proteins, reactionInst, null);
                    } else {
                        for (GKInstance catalystInst : catalystInstances) {
                            if (validCatalyst(catalystInst, goTerm)) {
                                Set<GKInstance> proteins = new HashSet<>();
                                if (goTerm.equals(MOLECULAR_FUNCTION)) {
                                    GKInstance activeUnitInst = (GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.activeUnit);
                                    GKInstance entityInst = activeUnitInst == null ? (GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity) : activeUnitInst;
                                    proteins = getMolecularFunctionProteins(entityInst);
                                } else if (goTerm.equals(BIOLOGICAL_PROCESS)) {
                                    proteins = getBiologicalProcessProteins((GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity));
                                }
                                processProteins(goTerm, proteins, reactionInst, catalystInst);
                            }
                        }
                    }
                }
            }
        }

        if (Files.exists(Paths.get(GOA_FILENAME))) {
            Files.delete(Paths.get(GOA_FILENAME));
        }
        List<String> sortedGoaLines = new ArrayList<>(goaLines);
        Collections.sort(sortedGoaLines);
        BufferedWriter br = new BufferedWriter((new FileWriter(GOA_FILENAME)));
        br.write("!gaf-version: 2.1\n");
        for (String goaLine : sortedGoaLines) {
//            br.append(goaLine + "\t" + dates.get(goaLine) + "\tReactome\t\t\n");
            ArrayList goals = new ArrayList<>(Arrays.asList(goaLine.split("\t")));
            String date = String.valueOf(dates.get(goaLine));
            goals.add(date);
            goals.add("Reactome");
            goals.add("");
            goals.add("");
            String goal = String.join("\t", goals);
            br.append(goal + "\n");
        }
        br.close();
    }

    private static Set<GKInstance> getReactionProteins(GKInstance reactionInst) throws Exception {
        List<ClassAttributeFollowingInstruction> classesToFollow = new ArrayList<>();
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Pathway, new String[]{ReactomeJavaConstants.hasEvent}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.ReactionlikeEvent, new String[]{ReactomeJavaConstants.input, ReactomeJavaConstants.output, ReactomeJavaConstants.catalystActivity}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Reaction, new String[]{ReactomeJavaConstants.input, ReactomeJavaConstants.output, ReactomeJavaConstants.catalystActivity}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.CatalystActivity, new String[]{ReactomeJavaConstants.physicalEntity}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Complex, new String[]{ReactomeJavaConstants.hasComponent}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.EntitySet, new String[]{ReactomeJavaConstants.hasMember}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Polymer, new String[]{ReactomeJavaConstants.repeatedUnit}, new String[]{}));

        String[] outClasses = new String[]{ReactomeJavaConstants.EntityWithAccessionedSequence};
        return InstanceUtilities.followInstanceAttributes(reactionInst, classesToFollow, outClasses);
    }

    private static Set<GKInstance> getMolecularFunctionProteins(GKInstance entityInst) throws Exception {
        Set<GKInstance> proteins = new HashSet<>();
        SchemaClass entitySchemaClass = entityInst.getSchemClass();
        if (!(entitySchemaClass.isa(ReactomeJavaConstants.Complex) || entitySchemaClass.isa(ReactomeJavaConstants.Polymer))) {
            if (entitySchemaClass.isa(ReactomeJavaConstants.EntitySet) && onlyEWASMembers(entityInst)) {
                proteins.addAll(entityInst.getAttributeValuesList(ReactomeJavaConstants.hasMember));
            } else if (entitySchemaClass.isa(ReactomeJavaConstants.EntityWithAccessionedSequence)) {
                proteins.add(entityInst);
            }
        }
        return proteins;
    }

    private static Set<GKInstance> getBiologicalProcessProteins(GKInstance physicalEntityInst) throws Exception {
        Set<GKInstance> physicalEntityInstances = new HashSet<>();
        if (multiInstancePhysicalEntity(physicalEntityInst.getSchemClass())) {
            physicalEntityInstances.addAll(getMultiInstanceSubInstances(physicalEntityInst));
        } else if (physicalEntityInst.getSchemClass().isa(ReactomeJavaConstants.EntityWithAccessionedSequence)) {
            physicalEntityInstances.add(physicalEntityInst);
        }
        return physicalEntityInstances;
    }

    private static boolean validCatalyst(GKInstance catalystInst, String goTerm) throws Exception {

        GKInstance physicalEntityInst = (GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity);
        if (physicalEntityInst != null && physicalEntityInst.getAttributeValue(ReactomeJavaConstants.compartment) != null) {
            if (goTerm.equals(MOLECULAR_FUNCTION)) {
                List<GKInstance> activeUnitInstances = catalystInst.getAttributeValuesList(ReactomeJavaConstants.activeUnit);
                if (activeUnitInstances.size() == 0 && !multiInstancePhysicalEntity(physicalEntityInst.getSchemClass())) {
                    return true;
                } else if (activeUnitInstances.size() == 1) {
                    SchemaClass activeUnitSchemaClass = (activeUnitInstances).get(0).getSchemClass();
                    if (activeUnitSchemaClass.isa(ReactomeJavaConstants.Complex) || activeUnitSchemaClass.isa(ReactomeJavaConstants.Polymer)) {
                        return false;
                    }
                    if (activeUnitSchemaClass.isa(ReactomeJavaConstants.EntitySet) && !onlyEWASMembers((activeUnitInstances).get(0))) {
                        return false;
                    }
                } else {
                    return false;
                }
                return true;
            } else {
                return true;
            }
        }
        return false;
    }

    private static void processProteins(String goTerm, Set<GKInstance> proteinInstances, GKInstance reactionInst, GKInstance catalystInst) throws Exception {

        for (GKInstance proteinInst : proteinInstances) {
            GKInstance referenceEntityInst = (GKInstance) proteinInst.getAttributeValue(ReactomeJavaConstants.referenceEntity);
            GKInstance speciesInst = (GKInstance) proteinInst.getAttributeValue(ReactomeJavaConstants.species);
            if (validPhysicalEntity(referenceEntityInst, speciesInst)) {
                String taxonIdentifier = ((GKInstance) speciesInst.getAttributeValue(ReactomeJavaConstants.crossReference)).getAttributeValue(ReactomeJavaConstants.identifier).toString();
                if (!microbialSpeciesToExclude.contains(taxonIdentifier)) {

                    if (goTerm.equals(CELLULAR_COMPONENT)) {
                        // TODO getCellularComponentGOALines
                        if (!speciesWithAlternateGOCompartment.contains(taxonIdentifier)) {
                            String goaLine = getGOCellularCompartmentLine(proteinInst, referenceEntityInst, reactionInst, taxonIdentifier);
                            int date = getDate(proteinInst);
                            if (dates.get(goaLine) == null) {
                                dates.put(goaLine, date);
                            } else {
                                if (date > dates.get(goaLine)) {
                                    dates.put(goaLine, date);
                                }
                            }
                            goaLines.add(goaLine);
                        }
                    }

                    if (goTerm.equals(MOLECULAR_FUNCTION)) {
                        // TODO getMolecularFunctionGOALines
                        if (catalystInst.getAttributeValue(ReactomeJavaConstants.activity) != null) {
                            for (String goaLine : getGOMolecularFunctionLine(proteinInst, catalystInst, referenceEntityInst, taxonIdentifier, reactionInst)) {
                                int date = getDate(catalystInst);
                                if (dates.get(goaLine) == null) {
                                    dates.put(goaLine, date);
                                } else {
                                    if (date > dates.get(goaLine)) {
                                        dates.put(goaLine, date);
                                    }
                                }
                                goaLines.add(goaLine);
                            }
                        }
                    }

                    if (goTerm.equals(BIOLOGICAL_PROCESS)) {
                        //TODO getBiologicalProcessGOALines
                        List<Map<String, String>> goBiologicalProcessAccessions = getGOBiologicalProcessAccessions(reactionInst, 0);
                        if (goBiologicalProcessAccessions != null) {
                            for (Map<String, String> biologicalProcess : goBiologicalProcessAccessions) {
                                String goaLine = getGOBiologicalProcessLine(proteinInst, referenceEntityInst, reactionInst, taxonIdentifier, biologicalProcess);
                                int date = getDate(reactionInst);
                                if (dates.get(goaLine) == null) {
                                    dates.put(goaLine, date);
                                } else {
                                    if (date > dates.get(goaLine)) {
                                        dates.put(goaLine, date);
                                    }
                                }
                                goaLines.add(goaLine);
                            }
                        }
                    }
                }
            }
        }
    }

    private static String getGOCellularCompartmentLine(GKInstance proteinInst, GKInstance referenceEntityInst, GKInstance reactionInst, String taxonIdentifier) throws Exception {
        List<String> goAnnotationLine = new ArrayList<>();
        //TODO: GO_CellularComponent check... is it needed?
        //TODO: Compartment check... is it needed?

        GKInstance reactionStableIdentifierInst = (GKInstance) reactionInst.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
        goAnnotationLine.add(uniprotDbString);
        goAnnotationLine.add(referenceEntityInst.getAttributeValue(ReactomeJavaConstants.identifier).toString());
        goAnnotationLine.add(getSecondaryIdentifier(referenceEntityInst));
        goAnnotationLine.add("");
        goAnnotationLine.add(getGOAccession(proteinInst));
        goAnnotationLine.add("REACTOME:" + reactionStableIdentifierInst.getAttributeValue(ReactomeJavaConstants.identifier).toString());
        goAnnotationLine.add("TAS");
        goAnnotationLine.add("");
        goAnnotationLine.add("C");
        goAnnotationLine.add("");
        goAnnotationLine.add("");
        goAnnotationLine.add("protein");
        goAnnotationLine.add("taxon:" + taxonIdentifier);
        return String.join("\t", goAnnotationLine);
    }

    private static List<String> getGOMolecularFunctionLine(GKInstance proteinInst, GKInstance catalystInst, GKInstance referenceEntityInst, String taxonIdentifier, GKInstance reactionInst) throws Exception {
        List<String> goAnnotationLines = new ArrayList<>();
        List<String> pubMedReferences = new ArrayList<>();
        Collection<GKInstance> literatureReferenceInstances = catalystInst.getAttributeValuesList(ReactomeJavaConstants.literatureReference);
        for (GKInstance literatureReferenceInst : literatureReferenceInstances) {
            pubMedReferences.add("PMID:" + literatureReferenceInst.getAttributeValue(ReactomeJavaConstants.pubMedIdentifier).toString());
        }
        GKInstance activityInst = (GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.activity);
        if (!activityInst.getAttributeValue(ReactomeJavaConstants.accession).toString().equals(PROTEIN_BINDING_ANNOTATION)) {
            if (pubMedReferences.size() > 0) {
                for (String pubMedReference : pubMedReferences) {
                    List<String> goAnnotationLine = new ArrayList<>();
                    goAnnotationLine.add(uniprotDbString);
                    goAnnotationLine.add(referenceEntityInst.getAttributeValue(ReactomeJavaConstants.identifier).toString());
                    goAnnotationLine.add(getSecondaryIdentifier(referenceEntityInst));
                    goAnnotationLine.add("");
                    goAnnotationLine.add("GO:" + activityInst.getAttributeValue(ReactomeJavaConstants.accession).toString());
                    goAnnotationLine.add(pubMedReference);
                    goAnnotationLine.add(pubMedReferences.size() > 0 ? "EXP" : "TAS");
                    goAnnotationLine.add("");
                    goAnnotationLine.add("F");
                    goAnnotationLine.add("");
                    goAnnotationLine.add("");
                    goAnnotationLine.add("protein");
                    goAnnotationLine.add("taxon:" + taxonIdentifier);
                    goAnnotationLines.add(String.join("\t", goAnnotationLine));
                }
            } else {
                GKInstance reactionStableIdentifierInst = (GKInstance) reactionInst.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
                List<String> goAnnotationLine = new ArrayList<>();
                goAnnotationLine.add(uniprotDbString);
                goAnnotationLine.add(referenceEntityInst.getAttributeValue(ReactomeJavaConstants.identifier).toString());
                goAnnotationLine.add(getSecondaryIdentifier(referenceEntityInst));
                goAnnotationLine.add("");
                goAnnotationLine.add("GO:" + activityInst.getAttributeValue(ReactomeJavaConstants.accession).toString());
                goAnnotationLine.add("REACTOME:" + reactionStableIdentifierInst.getAttributeValue(ReactomeJavaConstants.identifier).toString());
                goAnnotationLine.add(pubMedReferences.size() > 0 ? "EXP" : "TAS");
                goAnnotationLine.add("");
                goAnnotationLine.add("F");
                goAnnotationLine.add("");
                goAnnotationLine.add("");
                goAnnotationLine.add("protein");
                goAnnotationLine.add("taxon:" + taxonIdentifier);
                goAnnotationLines.add(String.join("\t", goAnnotationLine));
            }
        }
        return goAnnotationLines;
    }

    private static String getGOBiologicalProcessLine(GKInstance proteinInst, GKInstance referenceEntityInst, GKInstance reactionInst, String taxonIdentifier, Map<String, String> biologicalProcessAccession) throws Exception {
        List<String> goAnnotationLine = new ArrayList<>();
        goAnnotationLine.add(uniprotDbString);
        goAnnotationLine.add(referenceEntityInst.getAttributeValue(ReactomeJavaConstants.identifier).toString());
        goAnnotationLine.add(getSecondaryIdentifier(referenceEntityInst));
        goAnnotationLine.add("");
        goAnnotationLine.add(biologicalProcessAccession.get("accession"));
        goAnnotationLine.add(biologicalProcessAccession.get("event"));
        goAnnotationLine.add("TAS");
        goAnnotationLine.add("");
        goAnnotationLine.add("P");
        goAnnotationLine.add("");
        goAnnotationLine.add("");
        goAnnotationLine.add("protein");
        goAnnotationLine.add("taxon:" + taxonIdentifier);
        return String.join("\t", goAnnotationLine);
    }

    private static List<Map<String, String>> getGOBiologicalProcessAccessions(GKInstance reactionInst, int recursion) throws Exception {
        List<Map<String, String>> goBiologicalProcessAccessions = new ArrayList<>();
        if (recursion <= MAX_RECURSION_LEVEL) {
            Collection<GKInstance> goBiologicalProcessInstances = reactionInst.getAttributeValuesList(ReactomeJavaConstants.goBiologicalProcess);
            if (goBiologicalProcessInstances.size() > 0) {
                for (GKInstance goBiologicalProcessInst : goBiologicalProcessInstances) {
                    if (!goBiologicalProcessInst.getAttributeValue(ReactomeJavaConstants.accession).toString().equals(PROTEIN_BINDING_ANNOTATION)) {
                        Map<String, String> goBiologicalProcessAccession = new HashMap<>();
                        goBiologicalProcessAccession.put("accession", "GO:" + goBiologicalProcessInst.getAttributeValue(ReactomeJavaConstants.accession).toString());
                        GKInstance eventStableIdentifierInst = (GKInstance) reactionInst.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
                        goBiologicalProcessAccession.put("event", "REACTOME:" + eventStableIdentifierInst.getAttributeValue(ReactomeJavaConstants.identifier).toString());
                        goBiologicalProcessAccessions.add(goBiologicalProcessAccession);
                    }
                }
            } else {
                recursion++;
                Collection<GKInstance> hasEventReferralInstances = (Collection<GKInstance>) reactionInst.getReferers(ReactomeJavaConstants.hasEvent);
                if (hasEventReferralInstances != null) {
                    for (GKInstance hasEventReferralInst : hasEventReferralInstances) {
                        goBiologicalProcessAccessions.addAll(getGOBiologicalProcessAccessions(hasEventReferralInst, recursion));
                    }
                }
            }
        }
        return goBiologicalProcessAccessions;
    }

    private static String getGOAccession(GKInstance ewasInst) throws Exception {
        GKInstance compartmentInst = (GKInstance) ewasInst.getAttributeValue(ReactomeJavaConstants.compartment);
        if (compartmentInst != null && !compartmentInst.getAttributeValue(ReactomeJavaConstants.accession).toString().equals(PROTEIN_BINDING_ANNOTATION)) {
            return "GO:" + compartmentInst.getAttributeValue(ReactomeJavaConstants.accession).toString();
        }
        return null;
    }

    private static String getSecondaryIdentifier(GKInstance referenceEntityInst) throws Exception {
        if (referenceEntityInst.getAttributeValue(ReactomeJavaConstants.secondaryIdentifier) != null) {
            return referenceEntityInst.getAttributeValue(ReactomeJavaConstants.secondaryIdentifier).toString();
        } else if (referenceEntityInst.getAttributeValue(ReactomeJavaConstants.geneName) != null) {
            return referenceEntityInst.getAttributeValue(ReactomeJavaConstants.geneName).toString();
        } else {
            return referenceEntityInst.getAttributeValue(ReactomeJavaConstants.identifier).toString();
        }
    }

    private static Integer getDate(GKInstance proteinInst) throws Exception {
        String instanceDate = "";
        Collection<GKInstance> modifiedInstances = proteinInst.getAttributeValuesList(ReactomeJavaConstants.modified);
        if (modifiedInstances.size() > 0) {
            List<GKInstance> modifiedInstancesList = new ArrayList<>(modifiedInstances);
            GKInstance mostRecentModifiedInst = modifiedInstancesList.get(modifiedInstancesList.size() - 1);
            instanceDate = mostRecentModifiedInst.getAttributeValue(ReactomeJavaConstants.dateTime).toString();
        } else {
            GKInstance createdInst = (GKInstance) proteinInst.getAttributeValue(ReactomeJavaConstants.created);
            instanceDate = createdInst.getAttributeValue(ReactomeJavaConstants.dateTime).toString();
        }

        instanceDate = instanceDate.split(" ")[0];
        instanceDate = instanceDate.replaceAll("-", "");
        return Integer.valueOf(instanceDate);
    }

    private static boolean validPhysicalEntity(GKInstance referenceEntityInst, GKInstance speciesInst) throws Exception {
        if (referenceEntityInst != null && speciesInst != null) {
            GKInstance referenceDatabaseInst = (GKInstance) referenceEntityInst.getAttributeValue(ReactomeJavaConstants.referenceDatabase);
            if (referenceDatabaseInst != null && referenceDatabaseInst.getDisplayName().equals("UniProt") && speciesInst.getAttributeValue(ReactomeJavaConstants.crossReference) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean multiInstancePhysicalEntity(SchemaClass physicalEntitySchemaClass) {
        if (physicalEntitySchemaClass.isa(ReactomeJavaConstants.Complex) || physicalEntitySchemaClass.isa(ReactomeJavaConstants.EntitySet) || physicalEntitySchemaClass.isa(ReactomeJavaConstants.Polymer)) {
            return true;
        }
        return false;
    }

    private static Set<GKInstance> getMultiInstanceSubInstances(GKInstance physicalEntityInst) throws Exception {
        SchemaClass physicalEntitySchemaClass = physicalEntityInst.getSchemClass();
        String subunitType = null;
        Set<GKInstance> subInstanceProteins = new HashSet<>();
        if (physicalEntitySchemaClass.isa(ReactomeJavaConstants.Complex)) {
            subunitType = ReactomeJavaConstants.hasComponent;
        } else if (physicalEntitySchemaClass.isa(ReactomeJavaConstants.Polymer)) {
            subunitType = ReactomeJavaConstants.repeatedUnit;
        } else if (physicalEntitySchemaClass.isa(ReactomeJavaConstants.EntitySet)) {
            subunitType = ReactomeJavaConstants.hasMember;
        }
        for (GKInstance subunitInst : (Collection<GKInstance>) physicalEntityInst.getAttributeValuesList(subunitType)) {
            subInstanceProteins.addAll(getBiologicalProcessProteins(subunitInst));
        }
        return subInstanceProteins;
    }

    private static boolean onlyEWASMembers(GKInstance activeUnitInst) throws Exception {
        Collection<GKInstance> memberInstances = activeUnitInst.getAttributeValuesList(ReactomeJavaConstants.hasMember);
        if (memberInstances.size() > 0) {
            for (GKInstance memberInst : memberInstances) {
                if (!memberInst.getSchemClass().isa(ReactomeJavaConstants.EntityWithAccessionedSequence)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static boolean isInferred(GKInstance reactionInst) throws Exception {
        if (isElectronicallyInferred(reactionInst) || isManuallyInferred(reactionInst)) {
            return true;
        }
        return false;
    }

    private static boolean isManuallyInferred(GKInstance reactionInst) throws Exception {
        return reactionInst.getAttributeValue(ReactomeJavaConstants.inferredFrom) != null ? true : false;
    }

    private static boolean isElectronicallyInferred(GKInstance reactionInst) throws Exception {
        return reactionInst.getAttributeValue(ReactomeJavaConstants.evidenceType) != null ? true : false;
    }
}
