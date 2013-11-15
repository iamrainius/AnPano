package com.autonavi.panorama.util;

import android.util.Log;

public class AndroidLogger implements Logger {

	@Override
	public void log(String msg) {
		Log.d("AnPano", msg);
	}

}
