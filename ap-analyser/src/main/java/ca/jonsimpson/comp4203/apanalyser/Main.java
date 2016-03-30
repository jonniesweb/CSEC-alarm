package ca.jonsimpson.comp4203.apanalyser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.log4j.Logger;

public class Main {
	
	private static final String NO_CONFIG_MESSAGE = "Running with no configuration. No alerts will occur.";
	
	private final Logger log = Logger.getLogger(Main.class);
	
	private static final String PACKET_MONITOR_BINARY = "script/run-monitor";
	
	// the map of allowed access points
	private Map<String, Set<Entry>> whitelist;
	private String currentChannel;
	
	public static void main(String[] args) {
		new Main();
	}
	
	public Main() {
		
		loadWhitelist();
		
		// run the packet-monitor binary
		try {
			Process exec = Runtime.getRuntime().exec(PACKET_MONITOR_BINARY);
			InputStream inputStream = exec.getInputStream();
			
			InputStreamReader reader = new InputStreamReader(inputStream);
			BufferedReader bufferedReader = new BufferedReader(reader);
			
			while (true) {
				String readLine = bufferedReader.readLine();
				
				// System.out.println(readLine);
				if (readLine == null) {
					break;
				}
				processLine(readLine);
			}
			
		} catch (IOException e) {
			log.error(e);
		}
		
	}
	
	private void loadWhitelist() {
		Path path = Paths.get("./apanalyser.properties");
		File file = path.toFile();
		log.info("reading config file from: " + file.getAbsolutePath());
		
		whitelist = new HashMap<String, Set<Entry>>();
		
		if (!file.exists()) {
			log.warn(NO_CONFIG_MESSAGE);
			return;
		}
		
		// get a reader of the configuration file
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(
				file)))) {
			
			// iterate over each line, reading the configuration
			while (true) {
				String line = reader.readLine();
				if (line == null) {
					break;
				}
				
				Entry entry = getEntryFromLine(line);
				if (entry != null) {
					Set<Entry> set = whitelist.get(entry.ssid);
					// check if ssid has a set already, create on otherwise
					if (set == null) {
						set = new HashSet<Entry>();
						whitelist.put(entry.ssid, set);
					}
					
					set.add(entry);
					
				}
				
			}
			
		} catch (FileNotFoundException e) {
			log.warn(NO_CONFIG_MESSAGE);
		} catch (IOException e) {
			log.error(e);
		}
		
	}
	
	private void processLine(String line) {
		
		if (line == null || line.length() == 0) {
			return;
		}
		
		System.out.println(line);
		
		// check if this is a changing channel message
		if (line.charAt(0) == '#') {
			String[] strings = line.split(" ");
			currentChannel = strings[1];
			
		} else {
			// it's a message from the monitor
			Entry entry = getEntryFromLine(line);
			
			validateEntry(entry);
		}
		
	}
	
	/**
	 * Take a String and return a representation of it as an Entry.
	 * 
	 * @param line
	 * @return an Entry representing the line, null otherwise
	 */
	private Entry getEntryFromLine(String line) {
		String[] strings = line.split(",");
		
		String channel = strings[0];
		String mac = strings[1];
		String ssid = strings[2];
		
		Entry entry = new Entry(channel, mac, ssid);
		return entry;
	}
	
	/**
	 * The program only alerts when the same ssid is used and the channel or the
	 * mac address are not in the whitelist.
	 * 
	 * @param entry
	 */
	private void validateEntry(Entry entry) {
		
		Set<Entry> set = whitelist.get(entry.ssid);
		
		// if we don't have anything for this ssid, its not worth looking at
		if (set != null) {
			
			boolean contains = set.contains(entry);
			if (!contains) {
				// oh no, we don't recognize this Entry. Sound the alarm!
				alert(entry);
			}
		}
	}
	
	private void alert(Entry entry) {
		log.warn("Rouge AP detected! " + entry);
	}
	
	public static class Entry {
		
		private String channel;
		private String mac;
		private String ssid;
		
		public Entry(String channel, String mac, String ssid) {
			this.channel = channel;
			this.mac = mac;
			this.ssid = ssid;
		}
		
		@Override
		public String toString() {
			return "Entry [channel=" + channel + ", mac=" + mac + ", ssid=" + ssid + "]";
		}
		
		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Entry) {
				Entry other = (Entry) obj;
				return Objects.equals(channel, other.channel) && Objects.equals(mac, other.mac)
						&& Objects.equals(ssid, other.ssid);
			}
			
			return false;
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(channel, mac, ssid);
		}
	}
}
