package com.autonavi.panorama.camera;

import java.util.Iterator;
import java.util.List;

import com.autonavi.panorama.camera.DeviceManager;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PreviewCallback;
import android.util.Log;

@SuppressLint("NewApi")
public class CameraUtility {

	private static final String TAG = CameraUtility.class.getSimpleName();
	private final float fieldOfView;
	private boolean hasBackFacingCamera = false;
	private Camera.Size photoSize;
	private final Size previewSize;

	public CameraUtility(int width, int height) {
		Camera camera = openBackCamera();
		if (camera == null) {
			hasBackFacingCamera = false;
			previewSize = new Size(0, 0);
			fieldOfView = 0.0f;
			return;
		}
		

		hasBackFacingCamera = true;
		Camera.Size size = getClosetPreviewSize(camera, width, height);
		previewSize = new Size(size.width, size.height);
		fieldOfView = DeviceManager.getCameraFieldOfViewDegrees(camera
				.getParameters().getHorizontalViewAngle());
		camera.release();
	}

	public Camera openBackCamera() {
		int numCam = Camera.getNumberOfCameras();
		CameraInfo info = new CameraInfo();

		for (int i = 0; i < numCam; i++) {
			Log.d(TAG, "Alive!");
			Camera.getCameraInfo(i, info);
			Log.d(TAG, "Alive!");
			if (info.facing == CameraInfo.CAMERA_FACING_BACK) {
				return Camera.open(i);
			}
		}

		return null;
	}

	private Camera.Size getClosetPreviewSize(Camera cameraProxy, int width,
			int height) {
		List<Camera.Size> sizes = cameraProxy.getParameters()
				.getSupportedPictureSizes();
		int i = width * height;
		int j = Integer.MAX_VALUE;

		Camera.Size cameraSize = sizes.get(0);
		Iterator<Camera.Size> iterator = sizes.iterator();
		while (iterator.hasNext()) {
			Camera.Size size = iterator.next();
			int k = Math.abs(size.width * size.height - i);
			if (k >= j) {
				continue;
			}

			j = k;

			cameraSize = size;
		}

		return cameraSize;
	}

	public void allocateBuffers(Camera camera, Size size, int count,
			PreviewCallback callback) {
		camera.setPreviewCallbackWithBuffer(null);
		int i = (int) Math.ceil(ImageFormat.getBitsPerPixel(camera
				.getParameters().getPreviewFormat())
				/ 8.0F
				* (size.width * size.height));
		for (int j = 0; j < count; j++) {
			camera.addCallbackBuffer(new byte[i]);
		}

		camera.setPreviewCallbackWithBuffer(callback);
	}

	public float getFieldOfView() {
		return fieldOfView;
	}

	public String getFlashMode(Camera camera) {
		List<String> modes = camera.getParameters()
				.getSupportedFlashModes();
		if (modes != null && modes.contains("off")) {
			return "off";
		}

		return "auto";
	}

	public String getFocusMode(Camera camera) {
		List<String> modes = camera.getParameters()
				.getSupportedFocusModes();
		if (modes != null) {
			if (modes.contains("infinity")) {
				return "infinity";
			}

			if (modes.contains("fixed")) {
				return "fixed";
			}
		}

		return "auto";
	}

	public Camera.Size getPhotoSize() {
		return photoSize;
	}

	public Size getPreviewSize() {
		return previewSize;
	}

	public boolean hasBackFacingCamera() {
		return hasBackFacingCamera;
	}

	public void setFrameRate(Camera.Parameters params) {
		List<int[]> fpsRanges = params.getSupportedPreviewFpsRange();
		if (fpsRanges.size() == 0) {

			Log.d(TAG, "No suppoted frame rates returned!");
			return;
		}

		int[] range = new int[] { -1, -1 };

		Iterator<int[]> iterator = fpsRanges.iterator();
		while (iterator.hasNext()) {
			int[] fpsRange = iterator.next();

			if (fpsRange[1] <= range[1] || fpsRange[1] > 0x9c40) {
				if (fpsRange[1] == range[1]) {
					if (fpsRange[0] > range[0]) {
						range = fpsRange;
					}
				}
			} else {
				range = fpsRange;
			}

			Log.d(TAG, "Available rates : " + fpsRange[0] + " to "
					+ fpsRange[1]);
		}

		if (range[0] > 0) {
			Log.d(TAG, "Setting frame rate : " + range[0] + " to " + range[1]);
			params.setPreviewFpsRange(range[0], range[1]);
		}
	}

	public void setPictureSize(Camera.Parameters params, int base) {
		List<Camera.Size> sizes = params.getSupportedPictureSizes();

		int index = 0;
		int lastIndex = 0;
		int curWidth = 1000000000;

		while (index < sizes.size()) {
			Camera.Size cameraSize = sizes.get(index);
			int w = Math.abs(cameraSize.width - base);
			if (w < curWidth) {
				curWidth = w;
				lastIndex = index;
			}
			++index;
		}

		photoSize = sizes.get(lastIndex);
		params.setPictureSize(photoSize.width, photoSize.height);
		Log.e(TAG, "Photo size: " + photoSize.width + ", " + photoSize.height);
	}

}
