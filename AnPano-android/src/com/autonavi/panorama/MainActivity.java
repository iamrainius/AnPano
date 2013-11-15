package com.autonavi.panorama;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import com.autonavi.panorama.AnPano.Callback;
import com.autonavi.panorama.AnPano.SensorProxy;
import com.autonavi.panorama.camera.CameraPreview;
import com.autonavi.panorama.camera.CameraUtility;
import com.autonavi.panorama.camera.DeviceManager;
import com.autonavi.panorama.camera.TextureCameraPreview;
import com.autonavi.panorama.sensor.SensorReader;
import com.autonavi.panorama.util.AndroidLogger;
import com.autonavi.panorama.util.Log;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.math.Vector3;

public class MainActivity extends AndroidApplication implements Callback {

	private static final int MSG_START_CAMERA = 0x3;
	private static final int MSG_TAKE_PHOTO = 0x4;
	
	CameraPreview mCameraPreview;
	SensorReader mSensorReader;
	private boolean mCameraStopped = true;
	private boolean mTakingPhoto = false;
	
	private Handler mHandler = new MainHandler();
	private AnPano mRenderer;
	
	private HandlerThread mStorageThread;
	
	private Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback() {

		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {
			if (mTakingPhoto) {
				return;
			}
			
			if (!mCameraStopped) {
				if (mRenderer != null) {
					mRenderer.setImageData(data);
				}
			}
			
			mCameraPreview.returnCallbackBuffer(data);
		}
	};
	
	private PictureCallback mPictureCallback = new PictureCallback() {
		
		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			mCameraPreview.initCamera(mPreviewCallback, 320, 240, true);
			mTakingPhoto = false;
			
			writePictureToFile(data);
			
		}

	};
	
	private PictureCallback mRawPictureCallback  = new PictureCallback() {
		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
		}
	};
	
	private ShutterCallback mShutterCallback = new ShutterCallback() {
		@Override
		public void onShutter() {
		}
	};
	
	private float[] mOldOutput;
	
	private Handler mStorageHandler;
	private long mSessionTimestamp;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		mSensorReader = new SensorReader();
		mSensorReader.start(this);
		
		mStorageThread = new HandlerThread("mStorageThread");
		mStorageThread.start();
		mStorageHandler = new Handler(mStorageThread.getLooper());
		
		mSessionTimestamp = System.currentTimeMillis();
		
		AndroidApplicationConfiguration cfg = new AndroidApplicationConfiguration();
		cfg.useGL20 = true;

		mRenderer = new AnPano(this);
		mRenderer.setLogger(new AndroidLogger());
		mRenderer.setSensor(new SensorProxy() {
			
			@Override
			public double getHeadingDegrees() {
				
				float[] curOut = mSensorReader.getFilterOutput();
				if (mOldOutput != null) {
					updateTransform();
				}
				
				return mSensorReader.getHeadingDegrees();
			}

			@Override
			public float[] getFilterOutput() {
//				float[] curOut = mSensorReader.getFilterOutput();
//				if (mOldOutput != null) {
//					updateTransform(curOut);
//				}
				
				updateTransform();
				//mSensorReader.setHeadingDegrees(0.0);
				mOldOutput = mSensorReader.getFilterOutput();
				
				float[] rotation = new float[16];
								
				Matrix.transposeM(rotation, 0, mOldOutput, 0);
				return rotation;
			}

			@Override
			public float[] getRotationInDeg() {
				return mSensorReader.getRotationInDeg();
			}

			@Override
			public float getDeltaHeading() {
				return (float) mDeltaHeadingStep;
			}
		});
		
		initialize(mRenderer, cfg);
		
	}
	
	Vector3 oldForwardVec = new Vector3(0.0f, 0.0f, 0.0f);
	Vector3 newForwardVec = new Vector3(0.0f, 0.0f, 0.0f);
	private double mDeltaHeading = 0.0;
	private double mDeltaHeadingStep = 0.0;
	
	private void updateTransform() {
		oldForwardVec = newForwardVec.cpy();
		
		newForwardVec = mRenderer.getCameraDirection().cpy();
		////Log.log("Delta: " + newForwardVec.x + ", " + newForwardVec.y + ", " + newForwardVec.z);
		newForwardVec.y = 0.0f;
		newForwardVec = newForwardVec.nor();
		
		// 两向量点积，两向量点积等于两向量大小以及夹角余弦之积
		// 这里，两个向量都是单位向量，因此d即为cos(theta)
		double cosine = Math.max(
				Math.min(oldForwardVec.dot(newForwardVec), 1.0), -1.0);

		// 相邻两方向向量的夹角度数？
		mDeltaHeading  = Math.toDegrees(Math.acos(cosine));
		// 叉积所得向量中的y分支（此时x, z都为0）？
		// old x new
		if (oldForwardVec.z * newForwardVec.x - oldForwardVec.x
				* newForwardVec.z > 0.0) {
			mDeltaHeading *= -1;
		}

		// 45等份？
		mDeltaHeadingStep  = mDeltaHeading / 45.0;
		
		if (mDeltaHeading != 0.0) {
			if (Math.abs(mDeltaHeading) < 2.0 * Math
					.abs(mDeltaHeadingStep)) {
				Log.log("Delta: " + mDeltaHeading);
				mSensorReader.setHeadingDegrees(mSensorReader
						.getHeadingDegrees() + mDeltaHeading);
				mDeltaHeading = 0.0;
			} else {
				Log.log("Delta step: " + mDeltaHeadingStep);
				mSensorReader.setHeadingDegrees(mSensorReader
						.getHeadingDegrees() + mDeltaHeadingStep);
				mDeltaHeading -= mDeltaHeadingStep;
			}
		}
			
		Log.log(String.valueOf(mSensorReader.getHeadingDegrees()));
	}

	@Override
	protected void onResume() {
		super.onResume();
		initCamera();
	}

	private void initCamera() {
		CameraUtility cameraUtility = new CameraUtility(320, 240);
		mCameraPreview = new TextureCameraPreview(cameraUtility);
		Log.log("========== initCamera ==========");
		mCameraPreview.initCamera(mPreviewCallback, 320, 240, true);
		startCamera();
	}

	private void startCamera() {
		Log.log("========== startCamera ==========");
		if (mCameraPreview == null) {
			return;
		}
		
		mCameraStopped = false;
		mHandler.sendEmptyMessage(MSG_START_CAMERA);
	}
	
	private void stopCamera() {
		mCameraStopped = true;
	
		if (mCameraPreview == null) {
			return;
		}
		
		mCameraPreview.releaseCamera();
		mCameraPreview = null;
		
		// Todo:
		// 写Meta data
		// 关闭文件IO
	}
	
	public synchronized void takePhoto() {
		Camera camera = mCameraPreview.getCamera();
		if (camera == null) {
			Log.log("Unable to take a photo: camera is null");
			return;
		}
		
		camera.setPreviewCallback(null);
		camera.takePicture(mShutterCallback, mRawPictureCallback,
				mPictureCallback);
	}
	
	@Override
	protected void onDestroy() {
		stopCamera();
		mSensorReader.stop();
		super.onDestroy();
	}

	@Override
	public float getFieldOfViewFromDevice() {
		float fov = DeviceManager.getOpenGlDefaultFieldOfViewDegrees();
		return fov;
	}
	
	private class MainHandler extends Handler {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_START_CAMERA:
				if (mCameraPreview == null) {
					return;
				}
				mTakingPhoto = false;
				mCameraPreview.startPreview();
				break;
				
			case MSG_TAKE_PHOTO:
				takePhoto();
				break;
			}
		}
		
	}
	
	private void writePictureToFile(byte[] imageData) {
		final byte[] data = imageData;
		mStorageHandler.post(new Runnable() {
			
			@Override
			public void run() {
				FileOutputStream out = null;
				File path = new File(getExternalFilesDir("data"),
						String.valueOf(mSessionTimestamp));
				if (!path.exists()) {
					path.mkdirs();
				}
				
				String imageName = "%d.jpg";
				imageName = String.format(imageName, System.currentTimeMillis());
				Log.log("Save a photo at: " + path.getAbsolutePath() + ", " + imageName);
				try {
					out = new FileOutputStream(new File(path, imageName));
					out.write(data);
					out.flush();
					out.close();
				} catch (FileNotFoundException e) {
				} catch (IOException e) {
				}
				
			}
		});
	}



	@Override
	public void requestPhoto() {
		if (mTakingPhoto) {
			return;
		}
		
		mHandler.sendEmptyMessage(MSG_TAKE_PHOTO);
		mTakingPhoto = true;
	}
	
}