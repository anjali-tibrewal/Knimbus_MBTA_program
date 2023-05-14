package com.Knimbus.MBTA.Models;

public class DeparturesFromParkStreetModel {

	private String destination;
	private String route;
	private long minutesUntilDeparture;
	public String getDestination() {
		return destination;
	}
	public void setDestination(String destination) {
		this.destination = destination;
	}
	public String getRoute() {
		return route;
	}
	public void setRoute(String route) {
		this.route = route;
	}
	public long getMinutesUntilDeparture() {
		return minutesUntilDeparture;
	}
	public void setMinutesUntilDeparture(long minutesUntilDeparture) {
		this.minutesUntilDeparture = minutesUntilDeparture;
	}
	
	
}
