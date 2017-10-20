package es.jmrs.runbooster;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
//import android.util.Log;

public class SpeedBroadcastReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		//Log.d("SpeedBroadcastReceiver", "onReceive");
		
		MainActivity mainActivity = MainActivity.getInstance();
		if (mainActivity == null)
			return;
		
		String action = intent.getAction();
		
		//Log.d("SpeedBroadcastReceiver", "onReceive: " + action);
    		
		Bundle bundle = intent.getExtras();
		
		if (action == SpeedService.SPEED_UPDATE) {
			mainActivity.handleNewSpeed(bundle);
		}
		
		if (action == SpeedService.ERROR_REPORT) {
			String error = bundle.getString(SpeedService.ERROR_STRING);
			mainActivity.uncheckGpsSwitch(error);
			
		}
	}
}