package com.autonavi.panorama.camera;

import android.hardware.Camera;
import android.opengl.GLSurfaceView;

public abstract interface CameraPreview {
	public abstract Camera getCamera();

	public abstract Camera.Size getPhotoSize();

	public abstract float getReportedHorizontalFovDegrees();

	public abstract Size initCamera(Camera.PreviewCallback previewCallback,
			int width, int height, boolean paramBoolean);

	public abstract void releaseCamera();

	public abstract void returnCallbackBuffer(byte[] buffer);

	public abstract void setFastShutter(boolean enabled);

	public abstract void setMainView(GLSurfaceView view);

	public abstract void startPreview();
}
