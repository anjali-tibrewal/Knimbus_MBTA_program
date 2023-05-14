package com.Knimbus.MBTA;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.Knimbus.MBTA.Models.DeparturesFromParkStreetModel;

@SpringBootApplication
public class DeparturesFromParkStreet {


	private static String apiKey = "d9c100eb29f142c78730bff2c08cf0e3";
	private static String stationId = "place-pktrm"; // Park Street Station ID
	private static String stopId = "70200";

	private static final String LOG_FILE_PREFIX = "log";
	private static final String LOG_FILE_SUFFIX = ".txt";
	private static String outputFilePath = "output.html";


	public static void main(String[] args) {
		getSchedule();
	}

	public static void getSchedule() {

		try {
			Logger logger = Logger.getLogger(DeparturesFromParkStreet.class.getName());

			// Create a unique log file name based on timestamp
			String logFileName = LOG_FILE_PREFIX + System.currentTimeMillis() + LOG_FILE_SUFFIX;

			FileHandler fileHandler = new FileHandler(logFileName);
			fileHandler.setFormatter(new SimpleFormatter());
			logger.addHandler(fileHandler);

			// Create URL object with the API endpoint
			logger.log(Level.INFO, "Creating URL object with the API endpoint");
			URL url = new URL(
					"https://api-v3.mbta.com/predictions?page[offset]=0&page[limit]=100&sort=departure_time&include=trip,route&filter[stop]="
							+ stationId + "&api_key=" + apiKey);

			// Open connection
			logger.log(Level.INFO, "Setting up a connection with MBTA API");
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();

			// Set request method
			connection.setRequestMethod("GET");

			// Read response
			BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			String line;
			StringBuilder response = new StringBuilder();
			while ((line = reader.readLine()) != null) {
				response.append(line);
			}
			reader.close();

			// Parse JSON response
			JSONObject jsonResponse = new JSONObject(response.toString());

			// Get predictions
			JSONArray predictions = jsonResponse.getJSONArray("data");
			List<DeparturesFromParkStreetModel> departures = new ArrayList<>();
			int numberOfDepartures = 0;
			for (int i = 0; i < predictions.length(); i++) {
				DeparturesFromParkStreetModel departure = new DeparturesFromParkStreetModel();
				JSONObject prediction = predictions.getJSONObject(i);
				JSONObject attributes = prediction.getJSONObject("attributes");
				var arrivalTime = attributes.get("arrival_time");
				var departureTime = attributes.get("departure_time");
				if (prediction.getJSONObject("relationships").getJSONObject("stop").getJSONObject("data").get("id")
						.equals(stopId) && !departureTime.equals(null) && !arrivalTime.equals(null)
						&& ZonedDateTime.parse(arrivalTime.toString()).isAfter(ZonedDateTime.now(ZoneId.of("UTC-4")))
						&& numberOfDepartures < 10) {
					String tripId = prediction.getJSONObject("relationships").getJSONObject("trip")
							.getJSONObject("data").get("id").toString();
					logger.log(Level.INFO, "Fetching Destination");
					String destination = getDestination(tripId, jsonResponse);
					String routeId = prediction.getJSONObject("relationships").getJSONObject("route")
							.getJSONObject("data").get("id").toString();
					logger.log(Level.INFO, "Fetching Route");
					String route = getRoute(routeId, jsonResponse);

					logger.log(Level.INFO, "Calculating Mintutes until departure");
					long minutesUntilDeparture = calculateDepartureTime(departureTime);

					departure.setDestination(destination);
					departure.setMinutesUntilDeparture(minutesUntilDeparture);
					departure.setRoute(route);

					departures.add(departure);
					numberOfDepartures++;
					logger.log(Level.INFO, "Number of departures fetched : " + numberOfDepartures);
				}

			}
			Map<String, List<DeparturesFromParkStreetModel>> departuresGrouped = departures.stream()
					.collect(Collectors.groupingBy(w -> w.getRoute()));

			generateOutput(departuresGrouped);
	        
			// Close connection
			connection.disconnect();

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private static void generateOutput(Map<String, List<DeparturesFromParkStreetModel>> departuresGrouped) {
		StringBuilder output = new StringBuilder();
		output.append("<html>"
				+ "<head>"
				+ "<style>\n"
				+ "table {\n"
				+ "  font-family: arial, sans-serif;\n"
				+ "  border-collapse: collapse;\n"
				+ "  width: 50%;\n"
				+ "}\n"
				+ "\n"
				+ "td, th {\n"
				+ "  border: 1px solid #dddddd;\n"
				+ "  text-align: left;\n"
				+ "  padding: 8px;\n"
				+ "}\n"
				+ "\n"
				+ "tr:nth-child(even) {\n"
				+ "  background-color: #dddddd;\n"
				+ "}\n"
				+ "</style>"
				+ "<title>Departures From Park Street Station </title>"
				+ "</head>"
				+ "<body>");
		// Get current time in UTC-4 zone
		String currentTime = ZonedDateTime.now(ZoneId.of("UTC-4"))
				.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
		output.append("<h4><b>Current Time: </b>" + currentTime + "</h4>");
		output.append("<hr style=\"width:50%;text-align:left;margin-left:0\">");

		departuresGrouped.forEach((key, value) -> {
			output.append("<h3><b>----" + key + "----</b></h3>");
			output.append("<table style='font-size:10px'>"
					+ "<tbody>"
					+ "<tr>"
					+ "<th>Destination</th>"
					+ "<th>Schedule</th>"
					+ "</tr>");
			List<DeparturesFromParkStreetModel> departuresFromParkStreet = value;
			departuresFromParkStreet.stream().forEach(x -> {
				output.append("<tr>"
						+ "<td>" + x.getDestination() + "</td>"
						+ "<td>Departing in " + x.getMinutesUntilDeparture() + " minutes</td>"
						+ "</tr>");
			});
			output.append("</tbody>");
		});
		
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath))) {
		    writer.write(output.toString());

		    // Open the HTML file in the default browser
		    Desktop.getDesktop().browse(java.nio.file.Paths.get(outputFilePath).toUri());
		} catch (IOException e) {
		    e.printStackTrace();
		}
	}

	private static String getRoute(String routeId, JSONObject response) {
		JSONArray includedInformations = response.getJSONArray("included");
		for (int i = 0; i < includedInformations.length(); i++) {
			JSONObject includedInfo = includedInformations.getJSONObject(i);
			if (includedInfo.get("type").toString().equals("route")
					&& includedInfo.get("id").toString().equals(routeId)) {
				return includedInfo.getJSONObject("attributes").get("long_name").toString();
			}
		}
		return null;
	}

	private static long calculateDepartureTime(Object departureTime) {

		long minutesUntilDeparture = ZonedDateTime.now(ZoneId.of("UTC-4"))
				.until(ZonedDateTime.parse(departureTime.toString()), ChronoUnit.MINUTES);

		return minutesUntilDeparture;
	}

	private static String getDestination(String tripId, JSONObject response) {

		JSONArray includedInformations = response.getJSONArray("included");
		for (int i = 0; i < includedInformations.length(); i++) {
			JSONObject includedInfo = includedInformations.getJSONObject(i);
			if (includedInfo.get("type").toString().equals("trip")
					&& includedInfo.get("id").toString().equals(tripId)) {
				return includedInfo.getJSONObject("attributes").get("headsign").toString();
			}
		}
		return null;

	}


}
