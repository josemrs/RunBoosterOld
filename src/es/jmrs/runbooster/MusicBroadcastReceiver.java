package es.jmrs.runbooster;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
//import android.util.Log;

public class MusicBroadcastReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		//Log.d("MusicBroadcastReceiver", "onReceive");
		
		MainActivity mainActivity = MainActivity.getInstance();
		if (mainActivity == null)
			return;
		
		String action = intent.getAction();
		
		//Log.d("MusicBroadcastReceiver", "onReceive: " + action);
		
		if (action == MusicService.TRACK_STOPPED) {
			mainActivity.sendIntenToMusicService(MusicService.ACTION_STOP);
			mainActivity.setIsPlaying(false);
		}
		
		if (action == MusicService.TRACK_PLAYING) {
			mainActivity.setIsPlaying(true);
		}
		
		if (action == MusicService.TRACK_PAUSED) {
			mainActivity.setIsPlaying(false);
		}
		
		if (action == MusicService.LOAD_FINISHED) {
			mainActivity.setupTrackList();
		}
	}
}