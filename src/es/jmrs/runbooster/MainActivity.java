package es.jmrs.runbooster;

import java.text.NumberFormat;

import es.jmrs.runbooster.R;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
//import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;


public class MainActivity extends Activity {

	/*************************************************************************
	  Constants
	 ************************************************************************/
	private final static int SELECT_FILE_RESULT_CODE = 1;
	private final static long COUNTDOWN_TICK         = 1000;
	private final static long MS_IN_A_SECOND         = 1000;
	private final static String PREF_MINIMUM         = "minimumSpeed";
	private final static String PREF_TIME_BELOW_MIN  = "timeBelowMinimum";
	private final static String PREF_RANDOM_SWITCH   = "randomSwitch";
	private final static String STATE_IS_PLAYING     = "isPlaying";
	private final static String STATE_GPS_STATE      = "gpsState";
	private static enum GPS_STATUS {
		DISABLED,	// The GPS is not enabled in the device
		ENABLING,	// The settings has been shown to the user to enable the GPS
		ENABLED,	// The GPS is enabled in the device
		STARTED		// The speed is being tracked
	}
	private enum SPEED_INFO {
		ACTUAL_SPEED,
		TRACKING_PAUSED, 
		TRACKING_STOPPED,
		NO_SPEED_REPORTED
	}

	/*************************************************************************
	  Class variables
	 ************************************************************************/
	private static MainActivity mainActivity = null;
	
	
	/*************************************************************************
	  Private instance variables
	 ************************************************************************/
	private GPS_STATUS gpsStatus = GPS_STATUS.DISABLED;
	private boolean isPlaying = false;
	private float lowTriggerSpeed = -1;
	private int timeBelowMinimum = -1;
	
	// True: The speed info is shown as pace
	// False: The speed info is shown as speed
	private boolean display_pace = false; 
	private double current_speed = 0;

	private int selectedItem = -1;
	private ListView tracksListView;
	private View tracksListHeader;
	private TracksListAdapter tracksListAdapter;
	private Switch trackingSwitch;
	private Switch randomSwitch;
	private EditText minimumSpeedEditText;
	private EditText currentSpeedEditText;
	private EditText timeBelowMinimumEditText;
	private CountDownTimer countDownTimer;
	private LocationManager locationManager;
	private AdView adView;
	
	/*************************************************************************
	  Activity base classes overridden methods - Life cycle methods
	 ************************************************************************/

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		////Log.d("MusicActivity", "onCreate");
		
		setContentView(R.layout.activity_main);
		
		adView = (AdView) findViewById(R.id.adView);
		adView.loadAd(new AdRequest.Builder()
//						.addTestDevice("603FC1944B1CEA58501395B9180AA0CF")
						.build());

		tracksListView = (ListView) findViewById(R.id.tracksListView);
		trackingSwitch = (Switch) findViewById(R.id.trackingSwitch);
		trackingSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				
				if (isChecked && isPlaying) {
					uncheckGpsSwitch(R.string.stop_playback);
					return;
				}
				
				if (isChecked) {
					if (tracksListAdapter != null) {
						if(tracksListAdapter.isEmpty()) 
							uncheckGpsSwitch(R.string.no_tracks);
						else 
							checkAndEnableGps();
					}
				}
				else {
					stopSpeedService();
				}
			}
		});
		randomSwitch = (Switch) findViewById(R.id.randomSwitch);
		
		lowTriggerSpeed = Float.parseFloat(getString(R.integer.minimum_default_speed));
		timeBelowMinimum = Integer.parseInt(getString(R.integer.time_below_minimum_default));
		
		minimumSpeedEditText = (EditText) findViewById(R.id.minimumSpeedEditText);
		minimumSpeedEditText.addTextChangedListener(new TextWatcher() {
			
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}
			
			@Override
			public void afterTextChanged(Editable s) {
				
				float new_value = 0;
				Number number;
				try {
					NumberFormat nf = NumberFormat.getInstance();
					number = nf.parse(s.toString());	
					new_value = number.floatValue();
				} catch (Exception e) {
				}
				
				if (display_pace) {
					
					if (new_value > 0)
						lowTriggerSpeed = 60 / new_value;
				}
				else {
					lowTriggerSpeed = new_value;
				}
			}
		});
		
		timeBelowMinimumEditText = (EditText) findViewById(R.id.timeBelowMinimumEditText);
		timeBelowMinimumEditText.addTextChangedListener(new TextWatcher() {
			
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}
			
			@Override
			public void afterTextChanged(Editable s) {
				try {
					timeBelowMinimum = Integer.parseInt(s.toString());				
				} catch (Exception e) {
				}
			}
		});
		
		currentSpeedEditText = (EditText) findViewById(R.id.currentSpeedEditText);
		displayNewSpeed(SPEED_INFO.TRACKING_STOPPED);
	}

	@Override
	public void onStart() {
		super.onStart();
		//Log.d("MusicActivity", "onStart");
		mainActivity = this;
		sendIntenToMusicService(MusicService.ACTION_LOAD);
		
		SharedPreferences preferences = getPreferences(MODE_PRIVATE);
		minimumSpeedEditText.setText(
				preferences.getString(
						PREF_MINIMUM, getString(R.integer.minimum_default_speed)));
		timeBelowMinimumEditText.setText(
				preferences.getString(
						PREF_TIME_BELOW_MIN, getString(R.integer.time_below_minimum_default)));
		randomSwitch.setChecked(
				preferences.getBoolean(PREF_RANDOM_SWITCH, false));
	}
	
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
	    // Save the user's current game state
	    savedInstanceState.putBoolean(STATE_IS_PLAYING, isPlaying);
    	savedInstanceState.putInt(STATE_GPS_STATE, gpsStatus.ordinal());
	    
	    // Always call the superclass so it can save the view hierarchy state
	    super.onSaveInstanceState(savedInstanceState);
	}
	
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
	    // Always call the superclass so it can restore the view hierarchy
	    super.onRestoreInstanceState(savedInstanceState);
	   
	    // Restore state members from saved instance
	    isPlaying = savedInstanceState.getBoolean(STATE_IS_PLAYING);
	    gpsStatus = GPS_STATUS.values()[savedInstanceState.getInt(STATE_GPS_STATE)];
	}
	
	@Override
	public void onPause() {
		super.onPause();
		//Log.d("MusicActivity", "onPause");
	}
	
	@Override
	public void onResume() {	
		super.onResume();
		//Log.d("MusicActivity", "onResume");
		
		if (gpsStatus == GPS_STATUS.ENABLING)
			checkAndEnableGps();
		trackingSwitch.setChecked(gpsStatus == GPS_STATUS.STARTED);

	}
	
	@Override
	public void onStop() {
		//Log.d("MusicActivity", "onStop");
		
		// Save preferences
		SharedPreferences preferences = getPreferences(MODE_PRIVATE);
		SharedPreferences.Editor editor = preferences.edit();
		editor.putString(PREF_MINIMUM, Float.toString(lowTriggerSpeed));
		editor.putString(PREF_TIME_BELOW_MIN, Integer.toString(timeBelowMinimum));
		editor.putBoolean(PREF_RANDOM_SWITCH, randomSwitch.isChecked());
		editor.commit();

		super.onStop();
	}

	@Override
	public void onDestroy() {
		//Log.d("MusicActivity", "onDestroy");
		
		mainActivity = null;

		super.onDestroy();
	}
	
	@Override
	public void onBackPressed() {
		//Log.d("MusicActivity", "onBackPressed");
		
		if (isPlaying || gpsStatus == GPS_STATUS.STARTED)
		{
		    new AlertDialog.Builder(this)
		        .setTitle(R.string.exit)
		        .setMessage(R.string.exit_confirmation)
		        .setNegativeButton(android.R.string.no, null)
		        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
	
					@Override
					public void onClick(DialogInterface dialog, int which) {
						manualExit();
					}
		        }).create().show();
		}
		else 
		{
			manualExit();
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_exit) {
			manualExit();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		//Log.d("MusicActivity", "onActivityResult");
		
		switch(requestCode) {
			case SELECT_FILE_RESULT_CODE:
		    	 
				switch(resultCode) {
					case Activity.RESULT_OK:
						String filePath = Media.getPath(this, data.getData());
						
						Intent intent = new Intent(MusicService.ACTION_INSERT);
						intent.putExtra(MusicService.TRACK_PATH, filePath);
						startService(intent);
						
						break;
				
					case Activity.RESULT_CANCELED:
						//Log.d("MusicActivity", "Canceled");
						break;		    	 
				}
				break;
		}
     }
	
	/*************************************************************************
	  MusicActivity methods - Public methods
	 ************************************************************************/
	
	public static MainActivity getInstance() {
		return mainActivity;
	}
	
	public void setIsPlaying (boolean status) {
		isPlaying = status;
	}
	
	public void setupTrackList() {
		
		if (tracksListHeader == null) {
			tracksListHeader = getLayoutInflater().inflate(
					R.layout.track_list_header, null);
			
			tracksListView.addHeaderView(tracksListHeader);
			tracksListHeader.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view){
					//Log.d("MusicActivity", "tracksListHeader onClick");
					addTrack();
				}
			});
			tracksListView.setHeaderDividersEnabled(true);
			
			tracksListView.setOnItemClickListener(new OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent,
						View view, int position, long id) {
					
					if (gpsStatus == GPS_STATUS.STARTED) {
						Toast.makeText(getApplicationContext(),
								getString(R.string.disable_tracking),
								Toast.LENGTH_SHORT).show();
						
						view.setActivated(false);
						view.setSelected(false);
						
						return;
					}
					
					if (position == selectedItem) {
						selectedItem = -1; 
						view.setActivated(false);
						view.setSelected(false);
					}
					else {
						selectedItem = position;
					}
					
					//Log.d("MusicActivity", "tracksListItem onClick");
					sendIntenToMusicService(MusicService.ACTION_PLAY, position);
				}
			});
			tracksListView.setOnItemLongClickListener(new OnItemLongClickListener() {

				@Override
				public boolean onItemLongClick(AdapterView<?> arg0, View view,
						int position, long id) {
					
					if (gpsStatus == GPS_STATUS.STARTED) {
						Toast.makeText(getApplicationContext(),
								getString(R.string.disable_tracking),
								Toast.LENGTH_SHORT).show();
						
						view.setActivated(false);
						view.setSelected(false);
						
						return true;
					}
					
					selectedItem = -1; 
					
					Intent intent = new Intent(getApplicationContext(), MusicService.class);
					intent.setAction(MusicService.ACTION_REMOVE);
					intent.putExtra(MusicService.TRACK_POSITION, position);
					startService(intent);
					return true;
				}
			});
			tracksListAdapter = MusicService.getTrackListAdapter(this);
			tracksListView.setAdapter(tracksListAdapter);
		}
		tracksListAdapter.notifyDataSetChanged();
	}
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
	public void addTrack()
	{
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
        	intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        this.startActivityForResult(intent, SELECT_FILE_RESULT_CODE); 
	}
	
	public void handleNewSpeed(Bundle bundle) {
		
		current_speed = bundle.getDouble(SpeedService.SPEED_VALUE);
		displayNewSpeed(SPEED_INFO.ACTUAL_SPEED);
		
		// If the track list is empty do nothing
		if (tracksListAdapter == null || tracksListAdapter.isEmpty())
			return;
		
		if (isPlaying)
			return;
		
		// Speed is below the threshold
		if (current_speed < lowTriggerSpeed) {
		    
		    // If it is not already counting
		    if (countDownTimer == null) {
		    	
		    	// Start a count-down before trigger the reproduction
		    	countDownTimer = new CountDownTimer(timeBelowMinimum*MS_IN_A_SECOND,
		    			COUNTDOWN_TICK) {
					
					@Override
					public void onTick(long millisUntilFinished) {
						
						// Every tick in the count down check if speed is 
						// still below the threshold
						if (current_speed > lowTriggerSpeed) {
							countDownTimer.cancel();
							countDownTimer = null;
						}
					}
					
					@Override
					public void onFinish() {
						// If the count down finished and the speed is still 
						// lower than the minimum, start the reproduction.
						if (current_speed <= lowTriggerSpeed) {
							if (randomSwitch.isChecked())
								sendIntenToMusicService(MusicService.ACTION_PLAY, -2);
							else
								sendIntenToMusicService(MusicService.ACTION_PLAY, -1);
						}
						
						countDownTimer = null;
					}
				};
				countDownTimer.start();
		    }
		}
	}
	
	public void sendIntenToMusicService (String action) {
		sendIntenToMusicService (action, 0);
	}
	
	public void sendIntenToMusicService (String action, int track) {
		Intent intent = new Intent(getApplicationContext(), MusicService.class);
		intent.setAction(action);
		
		if (action.equals(MusicService.ACTION_PLAY))
			intent.putExtra(MusicService.TRACK_POSITION, track);
		
		startService(intent);
	}
	
	public void manualExit() {

		stopSpeedService();
		stopMusicService();
		
	    //this.finish();
	    Intent intent = new Intent(Intent.ACTION_MAIN);
	    intent.addCategory(Intent.CATEGORY_HOME);
	    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    startActivity(intent);
	}
	
	public static boolean isExternalStorageDocument(Uri uri) {
	    return "com.android.externalstorage.documents".equals(uri.getAuthority());
	}
	
	public void uncheckGpsSwitch(int adviceId) {
		uncheckGpsSwitch(getString(adviceId));
	}
	
	public void uncheckGpsSwitch(String toastText) 
	{
		trackingSwitch.setChecked(false);
		Toast.makeText(getApplicationContext(),
				toastText,
				Toast.LENGTH_SHORT).show();

		gpsStatus = GPS_STATUS.DISABLED;
	}
	
	/*************************************************************************
	  MusicActivity methods - Private methods
	 ************************************************************************/
	
	public void speedOnClick(View v) {

		TextView minimumSpeedTextView;
		TextView currentSpeedTextView;
		TextView speedUnitsTextView1;
		TextView speedUnitsTextView2;
		
		currentSpeedTextView = (TextView) findViewById(R.id.currentSpeedTextView);
		minimumSpeedTextView = (TextView) findViewById(R.id.minimumSpeedTextView);
		speedUnitsTextView1 = (TextView) findViewById(R.id.speedUnitsTextView1);
		speedUnitsTextView2 = (TextView) findViewById(R.id.speedUnitsTextView2);

		display_pace = !display_pace;
		
		if (display_pace) {
			currentSpeedTextView.setText(R.string.current_pace_label);
			minimumSpeedTextView.setText(R.string.minimum_pace);
			speedUnitsTextView1.setText(R.string.pace_units);
			speedUnitsTextView2.setText(R.string.pace_units);
			
			double pace = 0;
			
			if (lowTriggerSpeed > 0)
				pace = 1 / lowTriggerSpeed * 60;
			
			minimumSpeedEditText.setText(String.format("%.2f", pace));
		}
		else  {
			currentSpeedTextView.setText(R.string.current_speed_label);
			minimumSpeedTextView.setText(R.string.minimum_speed);
			speedUnitsTextView1.setText(R.string.speed_units);
			speedUnitsTextView2.setText(R.string.speed_units);
			
			minimumSpeedEditText.setText(String.format("%.2f", lowTriggerSpeed));
		}
		
		displayNewSpeed(SPEED_INFO.TRACKING_STOPPED);
    }
	
	private void checkAndEnableGps() {
		locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
		boolean currentGpsStatus = locationManager
		  .isProviderEnabled(LocationManager.GPS_PROVIDER);

		if (gpsStatus == GPS_STATUS.ENABLING && !currentGpsStatus) 
		{
			uncheckGpsSwitch(R.string.gps_not_enabled);
			gpsStatus = GPS_STATUS.DISABLED;
			return;
		}
		if (!currentGpsStatus) {
			showGpsEnableDialog();
			return;
		}
		gpsStatus = GPS_STATUS.ENABLED;
		startSpeedService();
	}
	
	private void showGpsEnableDialog() {
		 AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
	     
		 if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2){
			 alertDialogBuilder.setTitle(R.string.gps_is_required_older);
			 alertDialogBuilder.setMessage(R.string.gps_enable_question_older);
		 } else{
			 alertDialogBuilder.setTitle(R.string.gps_is_required_4_4);
			 alertDialogBuilder.setMessage(R.string.gps_enable_question_4_4);
		 }
		 
		 alertDialogBuilder.setPositiveButton(android.R.string.yes,
				 new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int id) {
					gpsStatus = GPS_STATUS.ENABLING;
					Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
					startActivity(intent);
					dialog.dismiss();
				}
			  });
		 alertDialogBuilder.setNegativeButton(android.R.string.no,
				 new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int id) {
					dialog.cancel();
				}
			});
		 
		 alertDialogBuilder.setOnCancelListener(new OnCancelListener() {
			
			@Override
			public void onCancel(DialogInterface dialog) {
				//Log.d("gpsEnableDialog", "onCancel");
				uncheckGpsSwitch(R.string.gps_not_enabled);
			}
		 });
		 		 
		 AlertDialog alertDialog = alertDialogBuilder.create();
		 alertDialog.show();
	}
	
	private void startSpeedService() {
		//Log.d("MusicActivity", "speedServiceStart");
		
		if (gpsStatus == GPS_STATUS.ENABLED) {
			
			Intent intent = new Intent(this, SpeedService.class);
			startService(intent);
			gpsStatus = GPS_STATUS.STARTED;
			displayNewSpeed(SPEED_INFO.NO_SPEED_REPORTED);
		}
	}
	
	private void stopSpeedService() {
		//Log.d("MusicActivity", "speedServiceStop");
		
		disableTimerIfCounting();
		
		Intent intent = new Intent(this, SpeedService.class);
		stopService(intent);
		
		gpsStatus = GPS_STATUS.ENABLED;
		displayNewSpeed(SPEED_INFO.TRACKING_STOPPED);
	}
	
	private void stopMusicService() {
		//Log.d("MusicActivity", "musicServiceStop");
		sendIntenToMusicService(MusicService.ACTION_STOP);
	}
	
	private void disableTimerIfCounting(){
		if (countDownTimer != null)
			countDownTimer.cancel();
		
		countDownTimer = null;
	}
	
	/*
	 * statusCode is used to display special information about the speed.
	 * 
	 * statusCode = 0 : Show the current speed.
	 * statusCode = -1: Show "||", speed tracking is on pause.
	 * statusCode = -2: Show "??", no speed information reported from location service.
	 * statusCode = -3: Show "--", speed tracking is stopped.
	 */
	private void displayNewSpeed(SPEED_INFO statusCode) {

		switch (statusCode) {
			case TRACKING_PAUSED:
				currentSpeedEditText.setText("||");
				currentSpeedEditText.setTextColor(Color.BLUE);
				break;
			case NO_SPEED_REPORTED:
				currentSpeedEditText.setText("??");
				currentSpeedEditText.setTextColor(Color.BLUE);
				break;
			case TRACKING_STOPPED:
				currentSpeedEditText.setText("--");
				currentSpeedEditText.setTextColor(Color.BLUE);
				break;
			
			default:
				
				if (display_pace) {
					double pace = 0;
					
					if (current_speed > 0)
						pace = 1 / current_speed * 60;
					
					currentSpeedEditText.setText(String.format("%.2f", pace));
				}
				else {
					currentSpeedEditText.setText(String.format("%.2f", current_speed));
				}
				
				if (current_speed <= lowTriggerSpeed)
					currentSpeedEditText.setTextColor(Color.RED);
				else
					currentSpeedEditText.setTextColor(Color.GREEN);	
				break;
		}
	}
}
