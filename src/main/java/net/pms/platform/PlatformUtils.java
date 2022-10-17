/*
 * This file is part of Universal Media Server, based on PS3 Media Server.
 *
 * This program is a free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2
 * of the License only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package net.pms.platform;

import com.sun.jna.Platform;
import com.sun.jna.platform.FileUtils;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import net.pms.Messages;
import net.pms.PMS;
import net.pms.newgui.LooksFrame;
import net.pms.platform.linux.LinuxUtils;
import net.pms.platform.mac.MacUtils;
import net.pms.platform.solaris.SolarisUtils;
import net.pms.platform.windows.WindowsUtils;
import net.pms.service.AbstractSleepWorker;
import net.pms.service.PreventSleepMode;
import net.pms.service.SleepManager;
import net.pms.util.PropertiesUtil;
import net.pms.util.Version;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base implementation for the {@link IPlatformUtils} class for the generic cases.
 * @author zsombor
 *
 */
public class PlatformUtils implements IPlatformUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(PlatformUtils.class);

	/** *  The singleton platform dependent {@link IPlatformUtils} instance */
	public static final IPlatformUtils INSTANCE = PlatformUtils.createInstance();
	protected static final Object IS_ADMIN_LOCK = new Object();
	protected static Boolean isAdmin = null;

	protected Path vlcPath;
	protected Version vlcVersion;
	protected boolean aviSynth;

	/** Only to be instantiated by {@link PlatformUtils#createInstance()}. */
	protected PlatformUtils() {
	}

	@Override
	public File getAvsPluginsDir() {
		return null;
	}

	@Override
	public File getKLiteFiltersDir() {
		return null;
	}

	@Override
	public String getShortPathNameW(String longPathName) {
		return longPathName;
	}

	@Override
	public String getWindowsDirectory() {
		return null;
	}

	@Override
	public String getDiskLabel(File f) {
		return null;
	}

	@Override
	public boolean isKerioFirewall() {
		return false;
	}

	@Override
	public Path getVlcPath() {
		return vlcPath;
	}

	@Override
	public Version getVlcVersion() {
		return vlcVersion;
	}

	@Override
	public boolean isAviSynthAvailable() {
		return aviSynth;
	}

	@Override
	public boolean isTsMuxeRCompatible() {
		return true;
	}

	@Override
	public String getiTunesFile() throws IOException, URISyntaxException {
		return null;
	}

	@Override
	public Charset getConsoleCharset() {
		return StandardCharsets.UTF_8;
	}

	@Override
	public boolean browseURI(String url) {
		try {
			URI uri = new URI(url);
			if (Platform.isLinux() && (Runtime.getRuntime().exec(new String[] {"which", "xdg-open"}).getInputStream().read() != -1)) {
				// Workaround for Linux as Desktop.browse() doesn't work on some Linux
				Runtime.getRuntime().exec(new String[] {"xdg-open", uri.toString()});
				return true;
			} else if (Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				Desktop.getDesktop().browse(uri);
				return true;
			} else if (Platform.isMac()) {
				// On OS X, open the given URI with the "open" command.
				// This will open HTTP URLs in the default browser.
				Runtime.getRuntime().exec(new String[] {"open", uri.toString() });
				return true;
			} else {
				LOGGER.error("Action BROWSE isn't supported on this platform");
			}
		} catch (IOException | URISyntaxException e) {
			LOGGER.trace("Unable to open the given URI: " + url + ".");
		}
		return false;
	}

	@Override
	public boolean isNetworkInterfaceLoopback(NetworkInterface ni) throws SocketException {
		return ni.isLoopback();
	}

	@Override
	public void addSystemTray(final LooksFrame frame) {
		if (SystemTray.isSupported()) {
			SystemTray tray = SystemTray.getSystemTray();

			Image trayIconImage = resolveTrayIcon();

			PopupMenu popup = new PopupMenu();
			MenuItem defaultItem = new MenuItem(Messages.getString("Quit"));
			MenuItem traceItem = new MenuItem(Messages.getString("MainPanel"));

			defaultItem.addActionListener((ActionEvent e) -> frame.quit());

			traceItem.addActionListener((ActionEvent e) -> frame.setVisible(true));

			if (PMS.getConfiguration().useWebInterfaceServer()) {
				MenuItem webInterfaceItem = new MenuItem(Messages.getString("WebInterface"));
				webInterfaceItem.addActionListener((ActionEvent e) -> browseURI(PMS.get().getWebInterfaceServer().getUrl()));
				popup.add(webInterfaceItem);
			}
			popup.add(traceItem);
			popup.add(defaultItem);

			final TrayIcon trayIcon = new TrayIcon(trayIconImage, PropertiesUtil.getProjectProperties().get("project.name"), popup);

			trayIcon.setImageAutoSize(true);
			trayIcon.addActionListener((ActionEvent e) -> {
				frame.setVisible(true);
				frame.setFocusable(true);
			});
			try {
				tray.add(trayIcon);
			} catch (AWTException e) {
				LOGGER.debug("Caught exception", e);
			}
		}
	}

	/**
	 * Fetch the hardware address for a network interface.
	 *
	 * @param ni Interface to fetch the mac address for
	 * @return the mac address as bytes, or null if it couldn't be fetched.
	 * @throws SocketException
	 *             This won't happen on Mac OS, since the NetworkInterface is
	 *             only used to get a name.
	 */
	@Override
	public byte[] getHardwareAddress(NetworkInterface ni) throws SocketException {
		return ni.getHardwareAddress();
	}

	/**
	 * Return the platform specific ping command for the given host address,
	 * ping count and packet size.
	 *
	 * @param hostAddress The host address.
	 * @param count The ping count.
	 * @param packetSize The packet size.
	 * @return The ping command.
	 */
	@Override
	public String[] getPingCommand(String hostAddress, int count, int packetSize) {
		return new String[] {"ping", /* count */"-c", Integer.toString(count), /* size */
				"-s", Integer.toString(packetSize), hostAddress};
	}

	@Override
	public String parsePingLine(String line) {
		int msPos = line.indexOf("ms");
		String timeString = null;

		if (msPos > -1) {
			if (line.lastIndexOf('<', msPos) > -1) {
				timeString = "0.5";
			} else {
				timeString = line.substring(line.lastIndexOf('=', msPos) + 1, msPos).trim();
			}
		}
		return timeString;
	}

	@Override
	public int getPingPacketFragments(int packetSize) {
		return ((packetSize + 8) / 1500) + 1;
	}

	protected String getTrayIcon() {
		return "icon-24.png";
	}

	/**
	 * Return the proper tray icon for the operating system.
	 *
	 * @return The tray icon.
	 */
	protected Image resolveTrayIcon() {
		String icon = getTrayIcon();
		return Toolkit.getDefaultToolkit().getImage(this.getClass().getResource("/resources/images/" + icon));
	}

	@Override
	public Double getWindowsVersion() {
		return null;
	}

	@Override
	public void moveToTrash(File file) throws IOException {
		FileUtils.getInstance().moveToTrash(new File[]{file});
	}

	@Override
	public List<Path> getDefaultFolders() {
		List<Path> result = new ArrayList<>();
		result.add(Paths.get("").toAbsolutePath());
		String userHome = System.getProperty("user.home");
		if (StringUtils.isNotBlank(userHome)) {
			result.add(Paths.get(userHome));
		}
		//TODO: (Nad) Implement xdg-user-dir for Linux when EnginesRegistration is merged:
		// xdg-user-dir DESKTOP
		// xdg-user-dir DOWNLOAD
		// xdg-user-dir PUBLICSHARE
		// xdg-user-dir MUSIC
		// xdg-user-dir PICTURES
		// xdg-user-dir VIDEOS
		return result;
	}

	@Override
	public boolean isAdmin() {
		return false;
	}

	@Override
	public String getDefaultFontPath() {
		return null;
	}

	@Override
	public boolean isPreventSleepSupported() {
		return false;
	}

	@Override
	public AbstractSleepWorker getSleepWorker(SleepManager owner, PreventSleepMode mode) {
		throw new IllegalStateException("Missing SleepWorker implementation for current platform");
	}

	private static PlatformUtils createInstance() {
		if (Platform.isWindows()) {
			return new WindowsUtils();
		}
		if (Platform.isMac()) {
			return new MacUtils();
		}
		if (Platform.isLinux()) {
			return new LinuxUtils();
		}
		if (Platform.isSolaris()) {
			return new SolarisUtils();
		}
		return new PlatformUtils();
	}

	protected static String getAbsolutePath(String path, String name) {
		File f = new File(path, name);
		if (f.exists()) {
			return f.getAbsolutePath();
		}
		return null;
	}
}