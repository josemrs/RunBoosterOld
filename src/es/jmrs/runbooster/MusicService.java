package es.jmrs.runbooster;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import es.jmrs.runbooster.R;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
//import android.util.Log;
import android.widget.Toast;

public class MusicService extends Service implements OnCompletionListener {
	
	/*************************************************************************
	  Constants
	 ************************************************************************/
	private static final int SERVICE_NOTIFICATION = 1;
	public final static String TRACK_PLAYING   = "es.jmrs.runbooster.broadcast.TRACK_PLAYING";
	public final static String TRACK_STOPPED   = "es.jmrs.runbooster.broadcast.TRACK_STOPPED";
	public final static String TRACK_FINISHED  = "es.jmrs.runbooster.broadcast.TRACK_FINISHED";
	public final static String TRACK_PAUSED    = "es.jmrs.runbooster.broadcast.TRACK_PAUSED";
	public final static String LOAD_FINISHED   = "es.jmrs.runbooster.broadcast.LOAD_FINISHED";
    public static final String ACTION_LOAD     = "es.jmrs.runbooster.action.LOAD";
    public static final String ACTION_PLAY     = "es.jmrs.runbooster.action.PLAY";
    public static final String ACTION_STOP     = "es.jmrs.runbooster.action.STOP";
    public static final String ACTION_INSERT   = "es.jmrs.runbooster.action.INSERT";
    public static final String ACTION_REMOVE   = "es.jmrs.runbooster.action.REMOVE";
    public static final String TRACK_POSITION  = "es.jmrs.runbooster.action.extra.TRACK_POSITION";
    public static final String TRACK_PATH      = "es.jmrs.runbooster.action.extra.TRACK_PATH";
	private final static String SAVE_FILE      = "tracks.sav";
	private static enum MUSIC_SERVICE_STATUS {
		UNLOADED,
		READY,
		PLAYING
	}
	
	/*************************************************************************
	  Class variables
	 ************************************************************************/
//	private static String TAG = "MusicService";
	private static ArrayList<TrackItem> trackList;
	private static TracksListAdapter tracksListAdapter;
	
	/*************************************************************************
	  Private instance variables
	 ************************************************************************/
	private NotificationManager notificationManager;
	private NotificationCompat.Builder notificationCompatBuilder;	
	private MediaPlayer mediaPlayer;
	private AudioManager audioManager;
	private OnAudioFocusChangeListener afChangeListener;
	private Random random;
	private Notification notification = null;
	private MUSIC_SERVICE_STATUS serviceStatus = MUSIC_SERVICE_STATUS.UNLOADED;
	private int DEFAULT_TRACK = -1; // -1 Means next track, no track selected.
	private long currentTrack = DEFAULT_TRACK;
	
	

	/*************************************************************************
	  Service base class overridden methods
	 ************************************************************************/	
	
    @Override
    public void onCreate() {
        //Log.i(TAG, "debug: Creating service");

        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationCompatBuilder = new NotificationCompat.Builder(this);
        Media.init(this);
        
        audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        
        afChangeListener = new OnAudioFocusChangeListener() {
            public void onAudioFocusChange(int focusChange) {
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                	pauseResumePlayback();
                } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                	pauseResumePlayback(); 
                } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                    audioManager.abandonAudioFocus(afChangeListener);
                    stopPlayback();
                } 
            }
        };
        
        
    }
    
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		//Log.d("MusicService", "onStartCommand");
		
		random = new Random(System.currentTimeMillis());
		
		String action = intent.getAction();
		if (action.equals(ACTION_LOAD)) loadTrackList();
		if (action.equals(ACTION_PLAY)) {
			int trackPosition = intent.getIntExtra(TRACK_POSITION, -1);
			startPlayback(tracksListAdapter.getItemId(trackPosition));
		};
		
		if (action.equals(ACTION_INSERT)) {
			String trackPath = intent.getStringExtra(TRACK_PATH);
			insertTrack(trackPath);
		};
		
		if (action.equals(ACTION_REMOVE)) {
			int trackPosition = intent.getIntExtra(TRACK_POSITION, -1);
			if (trackPosition > -1)
				removeTrack(trackPosition);
		};
		
		if (action.equals(ACTION_STOP)) stopPlayback();
		
		return(START_NOT_STICKY);
	}
	
	@Override
	public IBinder onBind(Intent intend) {
		//Log.d("MusicService", "onBind");
		return null;
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		//Log.d("MusicService", "onDestroy");
		
		if (mediaPlayer != null)
			mediaPlayer.release();
		
		Media.release();
		
		stopForeground(true);
	}
	
	/*************************************************************************
	  OnCompletionListener interface overridden methods
	 ************************************************************************/
	
	@Override
	public void onCompletion(MediaPlayer arg0) {
		stopPlayback();
	}
	
	/*************************************************************************
	  MusicService class methods - Public methods
	 ************************************************************************/
	
    public void createMediaPlayer() {
    	
        if (mediaPlayer != null) 
        	return;
    	mediaPlayer = new MediaPlayer();

        // Make sure the media player will acquire a wake-lock while playing. If we don't do
        // that, the CPU might go to sleep while the song is playing, causing playback to stop.
        //
        // Remember that to use this, we have to declare the android.permission.WAKE_LOCK
        // permission in AndroidManifest.xml.
    	mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);

        // we want the media player to notify us when it's ready preparing, and when it's done
        // playing:
//        	mediaPlayer.setOnPreparedListener(this);
    	mediaPlayer.setOnCompletionListener(this);
//          mPlayer.setOnErrorListener(this);
    }

	public void stopPlayback() {
		//Log.d("MusicService", "stop");
		
		if (mediaPlayer == null)
			return;
		
		if (mediaPlayer.isPlaying())
    	{
    		mediaPlayer.stop();
    		mediaPlayer.reset();
    	}

		mediaPlayer = null;
		audioManager.abandonAudioFocus(afChangeListener);
		this.sendBroadcast(TRACK_STOPPED);
		stopForeground(true);
		stopSelf();
	}
    
	public boolean existingTrack(String filePath)
	{
		int i;
		TrackItem item;
		
		for (i = 0; i < tracksListAdapter.getCount(); i++) {
			item = tracksListAdapter.getItem(i);
			
			if (item.getFilePath().equals(filePath)) {
				Toast.makeText(this, R.string.track_already_in_list, 
						Toast.LENGTH_LONG).show();
				return true;
			}
		}
		return false;
	}
	
	public static TracksListAdapter getTrackListAdapter(Context c) {

		tracksListAdapter = new TracksListAdapter(c, R.layout.track_list_item, 
												  trackList);
		return tracksListAdapter;
	}
	
	/*************************************************************************
	  MusicService class methods - Private methods
	 ************************************************************************/
    
    private void advanceTrack() {
		
		currentTrack++;
		currentTrack %= tracksListAdapter.getCount();
	}
    
    private void randomizeTrack() {
		
		currentTrack = random.nextInt(Integer.MAX_VALUE) % tracksListAdapter.getCount();
	}
	
    private boolean requestAudioFocus() {
    	
		// Request audio focus for playback
		int result = audioManager.requestAudioFocus(afChangeListener,
		                                 // Use the music stream.
		                                 AudioManager.STREAM_MUSIC,
		                                 // Request permanent focus.
		                                 AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
		   
		return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }
    
	private void startPlayback(long trackPosition) {
		//Log.d("MusicService", "startPlayback " + trackPosition);
		
		TrackItem track;
		
		createMediaPlayer();
		
		// trackPosition == -1: next song triggered by speed
		if (trackPosition == -1) {
			advanceTrack();
		}
		// trackPosition == -2: random song triggered by speed
		else if (trackPosition == -2) {
			randomizeTrack();
		}
		// trackPosition is an actual track number
		else {
			
			// if the selected track is the current 
			if (currentTrack == trackPosition-1) {
				
				// if playing, then pause
				if (mediaPlayer.isPlaying()) {
					mediaPlayer.pause();
					audioManager.abandonAudioFocus(afChangeListener);
					this.sendBroadcast(TRACK_PAUSED);
				}
				
				// if not playing, then resume
				else {
					
					// only if audio focus is gained
					if (this.requestAudioFocus()) {
					    mediaPlayer.start();
						this.sendBroadcast(TRACK_PLAYING);
					}
				}
				return;
			}
			
			// if the selected track is not the current one, then save the new position
			else {
				currentTrack = trackPosition-1;
			}
		}
		track = tracksListAdapter.getItem((int) currentTrack);
		mediaPlayer.reset();
    	
        try {
			mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			mediaPlayer.setDataSource(track.getFilePath());
			mediaPlayer.prepare();
			
			if (!this.requestAudioFocus())
				return;
			
			mediaPlayer.start();
			
			updateNotification(getString(R.string.music_playing) + " " + 
								track.getTitle());
			
			this.sendBroadcast(TRACK_PLAYING);
			
		} catch (IOException e) {
            //Log.e("Booster", "Could not open file " + track.getFilePath() + " for playback.", e);
		}
	}
	
	private void pauseResumePlayback() {

		if (currentTrack >= 0)
			// +1 emulates a call from the track list
			startPlayback(currentTrack+1); 
	}
	
	private void setNotification() {
	    
		Intent intent = new Intent(this, MainActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

		notification = notificationCompatBuilder
						    .setSmallIcon(R.drawable.app_icon)
						    .setContentTitle(getString(R.string.app_name))
						    .setContentIntent(pendingIntent)
						    .build();
		
		startForeground(SERVICE_NOTIFICATION, notification);
	}
	
	private void updateNotification(String text) {
		
		if (notification == null) 
			setNotification();
		
		notificationCompatBuilder.setContentText(text);
		notificationManager.notify(SERVICE_NOTIFICATION, notificationCompatBuilder.build());
    }

	private void removeTrack(int trackPosition) {
		
		if (currentTrack == trackPosition-1)
			stopPlayback();
		
		tracksListAdapter.removeItem(trackPosition-1); 
		storeTracks();
	}
	
	private void insertTrack(String trackPath) {
		
		Bundle metadata;
		TrackItem track;
		
		if (existingTrack(trackPath))
			return;
		
		metadata = Media.getSongMetadata(trackPath);
		
		if (metadata == null) 
			return;
		
		track = new TrackItem(trackPath, metadata);
		
		tracksListAdapter.addItem(track);
		storeTracks();
	}
	
	private void loadTrackList() {
		FileInputStream fis;
		
		if (serviceStatus == MUSIC_SERVICE_STATUS.UNLOADED)
		{
			trackList = new ArrayList<TrackItem>();
			
			try {
				fis = openFileInput(SAVE_FILE);
				
				StringBuilder builder = new StringBuilder();
				int ch;
				while((ch = fis.read()) != -1){
				    builder.append((char)ch);
				}
				
				fis.close();
				JSONArray jsonArray = new JSONArray(builder.toString());
		        new LoaderTask()
	        	.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
	        			jsonArray);
					
			} catch (FileNotFoundException e) {
				Toast.makeText(this, R.string.no_tracks, Toast.LENGTH_LONG).show();
			} 
			catch (Exception e) {
				Toast.makeText(this, R.string.track_error_loading, Toast.LENGTH_LONG).show();
				e.printStackTrace();
			}
			
			serviceStatus = MUSIC_SERVICE_STATUS.READY;
		}
		this.sendBroadcast(LOAD_FINISHED);
	}
	
	private void storeTracks() {
		JSONArray jsonArray = new JSONArray();
		JSONObject jsonObject;
		TrackItem item;
		FileOutputStream fos;
		try {
			fos = openFileOutput(SAVE_FILE, Context.MODE_PRIVATE);
			
			Iterator<TrackItem> i;
			
			for (i = trackList.iterator(); i.hasNext(); ) {
				item = i.next();
				jsonObject = new JSONObject().put(TrackItem.FILE_PATH,
												  item.getFilePath());
				jsonArray.put(jsonObject);
			}
			
			fos.write(jsonArray.toString().getBytes());
			fos.close();
		} catch (Exception e) {
			Toast.makeText(this, R.string.track_error_saving, Toast.LENGTH_LONG).show();
			e.printStackTrace();
		}
	}
	
	private void sendBroadcast(String action) {

		Intent intent = new Intent();
		intent.setAction(action);
		sendBroadcast(intent);
	}
	
	/*************************************************************************
	  LoaderTask for asynchronous list loading
	 ************************************************************************/
	
	private class LoaderTask extends AsyncTask<JSONArray, Integer, Void> {

	    @Override
	    protected void onPreExecute()
	    {
			Toast.makeText(getApplicationContext(),
					R.string.tracks_loading, Toast.LENGTH_SHORT).show();
	    }; 
		
		@Override
		protected Void doInBackground(JSONArray... jsonArray) {
			String filePath = null;
			Bundle metadata = null;
			
			JSONArray innerArray = jsonArray[0];
			for (int i=0; i < innerArray.length(); i++) {
				try {
					filePath = innerArray.getJSONObject(i).getString(TrackItem.FILE_PATH);
					metadata = Media.getSongMetadata(filePath);
					trackList.add(new TrackItem(filePath, metadata));
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
			
			return null;
		}

	    protected void onPostExecute(Void none) {
	    	super.onPostExecute(none);
	    	tracksListAdapter.notifyDataSetChanged();
	    	Toast.makeText(getApplicationContext(),
					R.string.tracks_loaded, Toast.LENGTH_SHORT).show();
	    }
	}
}
