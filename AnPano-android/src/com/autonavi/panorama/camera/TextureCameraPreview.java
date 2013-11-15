package com.autonavi.panorama.camera;

import java.io.IOException;

import android.annotation.SuppressLint;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.opengl.GLSurfaceView;
import android.util.Log;

@SuppressLint("NewApi")
public class TextureCameraPreview implements CameraPreview {

	private static final String TAG = TextureCameraPreview.class
			.getSimpleName();

	// Android Camera的代理对象
	private Camera mCamera = null;

	private final CameraUtility mCameraUtility;
	private String mFastShutterMode = "auto";
	private float mHorizontalViewAngle;
	private Camera.PreviewCallback mPreviewCallback;
	private Size mPreviewSize;

	// 针对纹理100进行图片流的获取
	private SurfaceTexture mSurfaceTexture = new SurfaceTexture(100);
	private boolean mUsePreviewBuffers;

	public TextureCameraPreview(CameraUtility cameraUtility) {
		mCameraUtility = cameraUtility;
	}

	private void setSceneMode(String mode) {
		Camera.Parameters params = mCamera.getParameters();
		params.setSceneMode(mode);
	}

	@Override
	public Camera getCamera() {
		return mCamera;
	}

	@Override
	public Camera.Size getPhotoSize() {
		return mCameraUtility.getPhotoSize();
	}

	@Override
	public float getReportedHorizontalFovDegrees() {
		return mHorizontalViewAngle;
	}

	@Override
	public Size initCamera(PreviewCallback previewCallback, int width,
			int height, boolean usePreviewBuffers) {

		Log.d(TAG, "init: w=" + width + ", h = " + height);

		mPreviewCallback = previewCallback;
		mUsePreviewBuffers = usePreviewBuffers;

		if (mCamera == null) {
			mCamera = mCameraUtility.openBackCamera();
		}

		if (mCamera == null) {
			Log.v(TAG, "Camera is null");
			return null;
		}

		mHorizontalViewAngle = mCamera.getParameters().getHorizontalViewAngle();
		Log.d(TAG, "mHorizontalViewAngle: " + mHorizontalViewAngle);

		Camera.Parameters params = mCamera.getParameters();
		params.setFocusMode(mCameraUtility.getFocusMode(mCamera));
		Log.d(TAG, "Focus mode: " + params.getFocusMode());
		params.setFlashMode(mCameraUtility.getFlashMode(mCamera));
		Log.d(TAG, "Flash mode: " + params.getFlashMode());
		params.setZoom(0);
		mPreviewSize = mCameraUtility.getPreviewSize();
		params.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
		Log.d(TAG, "Preview size: width=" + mPreviewSize.width + ", height="
				+ mPreviewSize.height);

		mCameraUtility.setFrameRate(params);
		//Log.d(TAG, "Frame rate: " );
		params.setJpegThumbnailSize(0, 0);
		params.setJpegThumbnailQuality(100);
		setPictureSize(params, 1000);
		Log.d(TAG, "Picture size: width=" + mCameraUtility.getPhotoSize().width
				+ ", height=" + mCameraUtility.getPhotoSize().height);

		params.setRotation(0);
		mCamera.setParameters(params);
		Log.d(TAG, "Field of view reported = " + params.getHorizontalViewAngle());
		Log.d(TAG, "Setting the preview display.");

		// 预览纹理对象设置
		try {
			mCamera.setPreviewTexture(mSurfaceTexture);
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (mUsePreviewBuffers) {
			mCameraUtility.allocateBuffers(mCamera, mPreviewSize, 3,
					mPreviewCallback);
		} else {
			mCamera.setPreviewCallback(mPreviewCallback);
		}

		return mPreviewSize;
	}

	private void setPictureSize(Parameters params, int base) {
		mCameraUtility.setPictureSize(params, base);
	}

	@Override
	public void releaseCamera() {
		if (mCamera == null) {
			return;
		}

		mCamera.stopPreview();
		mCamera.setPreviewCallback(null);
		mPreviewCallback = null;
		// mCamera.enableShutterSound(true);
		mCamera.release();
		mHorizontalViewAngle = 0.0f;
	}

	@Override
	public void returnCallbackBuffer(byte[] buffer) {
		if (!mUsePreviewBuffers) {
			return;
		}

		mCamera.addCallbackBuffer(buffer);
	}

	@Override
	public void setFastShutter(boolean enabled) {
		if (enabled) {
			setSceneMode(mFastShutterMode);
		} else {
			setSceneMode("auto");
		}
	}

	@Override
	public void setMainView(GLSurfaceView view) {
	}

	@Override
	public void startPreview() {
		mCamera.startPreview();
	}

}
