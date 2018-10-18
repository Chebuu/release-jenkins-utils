package org.reactome.release.downloadDirectory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

public class CreateReleaseTarball {

	public static void execute(int releaseNumber) throws IOException, InterruptedException {
		System.out.println("Running CreateReleaseTarball...");
		String tarDirRelease = "reactome_tar/" + releaseNumber + "/Release";
		String absReleaseDir = "/usr/local/gkb/scripts/release/website_files_update";
		String absReactomeDir = "/usr/local/reactomes/Reactome/production/Website/static/download/" + releaseNumber;
		Process makeReleaseDir = Runtime.getRuntime().exec("mkdir -p " + tarDirRelease);
		makeReleaseDir.waitFor();
		// Remove any existing Release repositorys
		File releaseDir = new File(tarDirRelease);
		if (releaseDir.list().length > 0) {
			Process removeReleaseRepo = Runtime.getRuntime().exec("rm -r " + tarDirRelease);
			removeReleaseRepo.waitFor();
		} 

		try {
			Git.cloneRepository().setURI("https://github.com/reactome/Release.git").setDirectory(Paths.get(tarDirRelease).toFile()).call();
		} catch (GitAPIException e) {
			e.printStackTrace();
		} 

		String tarDirGKB = "reactome_tar/" + releaseNumber + "/reactome/GKB";
		Runtime.getRuntime().exec("mkdir -p " + tarDirGKB);
		Process copyWebsite = Runtime.getRuntime().exec("cp -r " + tarDirRelease + "/website " + tarDirGKB);
		copyWebsite.waitFor();
		Runtime.getRuntime().exec("rm -f " + tarDirGKB + "/website/html/stats.html");
		Runtime.getRuntime().exec("rm -f " + tarDirGKB + "/website/html/stats.png");
		Runtime.getRuntime().exec("cp " + absReleaseDir + "/stats_v" + releaseNumber + ".html " + tarDirGKB + "/website/html/");
		Runtime.getRuntime().exec("cp " + absReleaseDir + "/stats_v" + releaseNumber + ".png " + tarDirGKB + "/website/html/");
		Runtime.getRuntime().exec("rm -fr " + tarDirGKB + "/website/html/download/");
		Runtime.getRuntime().exec("mkdir -p " + tarDirGKB + "/website/html/download/" + releaseNumber);
		Process copyReleaseHtml = Runtime.getRuntime().exec("cp -t"  + tarDirGKB + "/website/html/download/ " + tarDirRelease + "/website/html/download/all_interactions.html " + tarDirRelease + "/website/html/download/index.html ");
		copyReleaseHtml.waitFor();

		Process copyModules = Runtime.getRuntime().exec("cp -r " + tarDirRelease + "/modules " + tarDirGKB);
		copyModules.waitFor();
		
		// This step might not be needed anymore, according to a comment in the old code (make_release_tarball.pl)
		Runtime.getRuntime().exec("mkdir -p " + tarDirGKB + "/third_party_install");
		Process tarThirdPartyInstall = Runtime.getRuntime().exec("tar czf " + tarDirGKB + "/third_party_install/config.tar.gz -C " + tarDirRelease + "/third_party_install etc usr");
		tarThirdPartyInstall.waitFor();
		
		String analysisServiceDir = "reactome_tar/" + releaseNumber + "/reactome/AnalysisService";
		Runtime.getRuntime().exec("mkdir -p " + analysisServiceDir + "/temp");
		Runtime.getRuntime().exec("mkdir -p " + analysisServiceDir + "/input");
	
		Process copyFireworksDir = Runtime.getRuntime().exec("cp -r " + absReactomeDir + "/fireworks " + tarDirGKB + "/website/html/download/" + releaseNumber);
		copyFireworksDir.waitFor();
		
		Process copyDiagramDir = Runtime.getRuntime().exec("cp -r " + absReactomeDir + "/diagram " + tarDirGKB + "/website/html/download/" + releaseNumber);
		copyDiagramDir.waitFor();
		
		Process copyAnalysisBin = Runtime.getRuntime().exec("cp /usr/local/reactomes/Reactome/production/AnalysisService/input/analysis.bin " + analysisServiceDir + "/input");
		copyAnalysisBin.waitFor();
		
		// It usually created a 'RESTful' directory here. All it contains is an empty 'temp' subdirectory, so it wasn't included for this rewrite.
		
		// Tar everything
		Process reactomeTar = Runtime.getRuntime().exec("tar czf " + releaseNumber + "/reactome.tar.gz -C reactome_tar/" + releaseNumber + "/reactome/ .");
		reactomeTar.waitFor();
		
		Process removeReactomeTarDir = Runtime.getRuntime().exec("rm -r reactome_tar");
		removeReactomeTarDir.waitFor();
		
		
		// TODO: Solr, apache-tomcat(?), install_reactome.sh modification
		
	}
}
