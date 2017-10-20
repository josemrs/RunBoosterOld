package es.jmrs.runbooster;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationRequest;

import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
//import android.util.Log;

public class SpeedService extends Service implements 
	GooglePlayServicesClient.ConnectionCallbacks,
	GooglePlayServicesClient.OnConnectionFailedListener,
	com.google.android.gms.location.LocationListener 
	{
	
	
	/*************************************************************************
	  Constants
	 ************************************************************************/
	private final static int LOCATION_FASTEST_INTERVAL = 5000; // 5s
	private final static int LOCATION_UPDATE_INTERVAL  = 15000;  // 15s
	private final static int MIN_SPEED_TO_REPORT       = 1 * 1000 / 3600;  // 1km/h
	public final static String SPEED_UPDATE  = "es.jmrs.runbooster.broadcast.SPEED_UPDATE";
	public final static String ERROR_REPORT  = "es.jmrs.runbooster.broadcast.ERROR_REPORT";
	public final static String SPEED_VALUE   = "SPEED_VALUE";
	public final static String ERROR_STRING  = "ERROR_STRING";
	
	/*************************************************************************
	  Public instance variables
	 ************************************************************************/
	
	public LocationManager locationManager;
	public LocationClient locationClient;
	public LocationRequest locationRequest;
	public double speedMean = 0;

	/*************************************************************************
	  Service base class overridden methods
	 ************************************************************************/	
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		super.onStartCommand(intent, flags, startId);
		//Log.d("SpeedService", "onStartCommand");
		
		locationRequest = LocationRequest.create();
		locationRequest.setFastestInterval(LOCATION_FASTEST_INTERVAL);
		locationRequest.setInterval(LOCATION_UPDATE_INTERVAL);
		locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
		locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
		locationClient = new LocationClient(this, this, this);
		locationClient.connect();
		
		return(START_STICKY);
	}
	
	@Override
	public void onDestroy() {
		//Log.d("SpeedService", "onDestroy");
		locationClient.disconnect();
		stopSelf();
		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}
	
	/*************************************************************************
	  ConnectionCallbacks interface methods
	 ************************************************************************/	
	
	@Override
	public void onConnected(Bundle connectionHint) {
		locationClient.requestLocationUpdates(locationRequest, this);
		//Log.d("SpeedService", "onConnected");
	}

	@Override
	public void onDisconnected() {
		//Log.d("SpeedService", "onDisconnected");
		Intent intent = new Intent();
		intent.setAction(ERROR_REPORT);
		intent.putExtra(ERROR_STRING, "onDisconnected");
		sendBroadcast(intent);
	}

	/*************************************************************************
	  LocationListener interface methods
	 ************************************************************************/	
	
	@Override
	public void onLocationChanged(Location location) {

		//Log.d("SpeedService", "Current speed: " + location.getSpeed() + "m/s");
		
		double currentSpeed = 0.0;
		
		if (location.hasSpeed()) {
			currentSpeed = location.getSpeed(); 
		
			if (currentSpeed > MIN_SPEED_TO_REPORT) {
				// from m/s to km/h
				currentSpeed = currentSpeed / 1000 * 3600;
			}
		}
		
		speedMean = (speedMean + currentSpeed) / 2;
		//Log.d("SpeedService", "Mean speed: " + speedMean + "km/h");
		
		broadcastSpeed();
	}

	@Override
	public void onConnectionFailed(ConnectionResult result) {
		//Log.d("SpeedService", "onConnectionFailed");
		broadcastError(getString(R.string.gps_service_connection_failed));
	}

	/*************************************************************************
	  SpeedService methods
	 ************************************************************************/	
	
	private void broadcastSpeed() {
		Intent intent = new Intent();
		intent.setAction(SPEED_UPDATE);
		intent.putExtra(SPEED_VALUE, speedMean);
		sendBroadcast(intent);
	}
	
	private void broadcastError(String error) {
		Intent intent = new Intent();
		intent.setAction(ERROR_REPORT);
		intent.putExtra(ERROR_STRING, error);
		sendBroadcast(intent);
	}
}
