package fiji.updater;

import ij.IJ;
import ij.WindowManager;

import ij.plugin.PlugIn;

import fiji.updater.logic.Checksummer;
import fiji.updater.logic.PluginCollection;
import fiji.updater.logic.XMLFileDownloader;
import fiji.updater.logic.XMLFileReader;

import fiji.updater.ui.UpdaterFrame;
import fiji.updater.ui.ViewOptions;

import fiji.updater.ui.ViewOptions.Option;

import fiji.updater.util.Canceled;
import fiji.updater.util.Progress;
import fiji.updater.util.Util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import java.net.UnknownHostException;

import javax.swing.JOptionPane;

import javax.xml.parsers.ParserConfigurationException;

public class Updater implements PlugIn {
	public static String MAIN_URL = "http://pacific.mpi-cbg.de/update/";
	public static String UPDATE_DIRECTORY = "/var/www/update/";
	public static String SSH_HOST = "pacific.mpi-cbg.de";

	public static final String XML_COMPRESSED = "db.xml.gz";

	// Key names for ij.Prefs for saved values
	// Note: ij.Prefs is only saved during shutdown of Fiji
	public static final String PREFS_USER = "fiji.updater.login";

	public static boolean debug, testRun;

	public void run(String arg) {

		if (errorIfDebian())
			return;

		if (new File(Util.fijiRoot, "update").exists()) {
			IJ.error("Fiji restart required to finalize previous update");
			return;
		}

		final PluginCollection plugins = new PluginCollection();
		final UpdaterFrame main = new UpdaterFrame(plugins);
		main.setLocationRelativeTo(IJ.getInstance());
		main.setEasyMode(true);
		main.setVisible(true);
		WindowManager.addWindow(main);

		Progress progress = main.getProgress("Starting up...");
		XMLFileDownloader downloader = new XMLFileDownloader(plugins);
		downloader.addProgress(progress);
		try {
			downloader.start();
		} catch (Canceled e) {
			downloader.done();
			main.dispose();
			IJ.error("Canceled");
			return;
		} catch (Exception e) {
			e.printStackTrace();
			downloader.done();
			main.dispose();
			String message;
			if (e instanceof UnknownHostException)
				message = "Failed to lookup host "
					+ e.getMessage();
			else
				message = "Download/checksum failed: " + e;
			IJ.error(message);
			return;
		}

		progress = main.getProgress("Matching with local files...");
		Checksummer checksummer = new Checksummer(plugins, progress);
		try {
			if (debug)
				checksummer.done();
			else
				checksummer.updateFromLocal();
		} catch (Canceled e) {
			checksummer.done();
			main.dispose();
			IJ.error("Canceled");
			return;
		}

		if ("update".equals(arg)) {
			plugins.markForUpdate(false);
			main.setViewOption(Option.UPDATEABLE);
			if (testRun)
				System.err.println("forceable updates: "
					+ Util.join(", ",
						plugins.updateable(true))
					+ ", changes: "
					+ Util.join(", ", plugins.changes()));
			else if (plugins.hasForcableUpdates()) {
				main.warn("There are locally modified files!");
				if (Util.isDeveloper && !plugins.hasChanges()) {
					main.setViewOption(Option
							.LOCALLY_MODIFIED);
					main.setEasyMode(false);
				}
			}
			else if (!plugins.hasChanges())
				main.info("Your Fiji is up to date!");
		}

		main.updatePluginsTable();
	}

	/** This returns true if this seems to be the Debian packaged
	 * version of Fiji, or false otherwise. */

	public static boolean isDebian() {
		String debianProperty = System.getProperty("fiji.debian");
		return debianProperty != null && debianProperty.equals("true");
	}

	/** If this seems to be the Debian packaged version of Fiji,
	 * then produce an error and return true.  Otherwise return false. */

	public static boolean errorIfDebian() {
		// If this is the Debian / Ubuntu packaged version, then
		// insist that the user uses apt-get / synaptic instead:
		if (isDebian()) {
			String message = "You are using the Debian packaged version of Fiji.\n";
			message += "You should update Fiji with your system's usual package manager instead.";
			IJ.error(message);
			return true;
		} else
			return false;
	}

}
