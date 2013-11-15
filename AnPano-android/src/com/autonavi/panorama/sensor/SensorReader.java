package com.autonavi.panorama.sensor;

import java.util.Arrays;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.Matrix;
import android.os.Build;
import android.util.Log;

import com.google.android.apps.lightcycle.math.Vector3;
import com.google.android.apps.lightcycle.sensor.OrientationEKF;

public class SensorReader {
	public interface Callback<T> {
		void onCallback(T t);
	}
	
	// 加速度滤波系数
	private float accelFilterCoefficient = 0.15f;
	// 加速度
	private Vector3 acceleration = new Vector3();

	private float angularVelocitySqrRad = 0.0f;
	// 扩展卡尔曼滤波器
	//
	private OrientationEKF ekf = new OrientationEKF();

	private boolean filterInitialized = false;
	// 经过滤波的加速度
	private Vector3 filteredAcceleration = new Vector3();
	// 地磁场
	private float[] geomagnetic = new float[3];

	// 陀螺偏差
	private float[] gyroBias = { 0.0f, 0.0f, 0.0f };
	private long gyroLastTimestamp = 0;

	// IMU: 惯性测量单元？
	private float imuOrientationDeg = 90.0f;
	private int numGyroSamples = 0;
	private float[] rotationAccumulator = new float[3];

	private boolean useEkf = true;
	private SensorManager sensorManager = null;
	private Callback<Float> sensorVelocityCallback = null;
	private float[] tForm = new float[16];

	// 可见相关的sensor包括：TYPE_ACCELEROMETER，
	// TYPE_MAGNETIC_FIELD以及TYPE_GYROSCOPE
	private final SensorEventListener sensorEventListener = new SensorEventListener() {

		@Override
		public void onSensorChanged(SensorEvent event) {
			if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
				updateAccelerometerState(event);
				if (useEkf) {
					ekf.processAcc(event.values, event.timestamp);
				}
			} else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) { // :cond_1
				geomagnetic[0] = event.values[0];
				geomagnetic[1] = event.values[1];
				geomagnetic[2] = event.values[2];
			} else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) { // :cond_2
				// v4 = event.values[0]
				// v5 = gyroBias[0]
				event.values[0] -= gyroBias[0];
				event.values[1] -= gyroBias[1];
				event.values[2] -= gyroBias[2];
				angularVelocitySqrRad = event.values[0] * event.values[0]
						+ event.values[1] * event.values[1] + event.values[2]
						* event.values[2];

				if (sensorVelocityCallback != null) {
					sensorVelocityCallback.onCallback(angularVelocitySqrRad);
				}
				// :cond_3
				updateGyroState(event);
				if (useEkf) {
					ekf.processGyro(event.values, event.timestamp);
				}

			}
		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
		}
	};

	private void updateAccelerometerState(SensorEvent event) {
		acceleration.set(event.values[0], event.values[1], event.values[2]);
		if (!filterInitialized) {
			filteredAcceleration.set(event.values[0], event.values[1],
					event.values[2]);
			filterInitialized = true;
		} else {
			filteredAcceleration.x = accelFilterCoefficient * event.values[0]
					+ (1.0f - accelFilterCoefficient) * filteredAcceleration.x;
			filteredAcceleration.y = accelFilterCoefficient * event.values[1]
					+ (1.0f - accelFilterCoefficient) * filteredAcceleration.y;
			filteredAcceleration.z = accelFilterCoefficient * event.values[2]
					+ (1.0f - accelFilterCoefficient) * filteredAcceleration.z;
		}
	}

	private void updateGyroState(SensorEvent event) {
		if (gyroLastTimestamp != 0) {
			float f = 1.0e-9f * (float) (event.timestamp - gyroLastTimestamp);
			synchronized (this) {
				rotationAccumulator[0] += f * event.values[0];
				rotationAccumulator[1] += f * event.values[1];
				rotationAccumulator[2] += f * event.values[2];
				numGyroSamples++;
			}
		}

		gyroLastTimestamp = event.timestamp;
	}

	public void enableEkf(boolean enabled) {
		useEkf = enabled;
	}

	// 加速度在XY平面投影与X轴的夹角
	public float getAccelInPlaneRotationRadians() {
		return (float) Math.atan2(filteredAcceleration.y,
				filteredAcceleration.x);
	}

	public float[] getAndResetGyroData() {
		synchronized (this) {
			float[] curRotationAccu = (float[]) rotationAccumulator.clone();
			rotationAccumulator[0] = 0.0f;
			rotationAccumulator[1] = 0.0f;
			rotationAccumulator[2] = 0.0f;
			numGyroSamples = 0;
			return curRotationAccu;
		}
	}

	public float getAngularVelocitySquredRad() {
		return angularVelocitySqrRad;
	}

	// Azimuth:　方位角
	public int getAzimuthInDeg() {
		float[] rotationMatrix = new float[16];
		float[] rotation = new float[3];

		// getRotationMatrix(): 计算旋转矩阵
		// 这个旋转矩阵将向量由设备坐标系变换到世界坐标系：
		// X: YZ的向量积，与地面相切，指向东
		// Y: 与地面相切指向北
		// Z: 垂直于地面指向天空
		SensorManager.getRotationMatrix(rotationMatrix, null,
				filteredAcceleration.toFloatArray(), geomagnetic);

		// 基于旋转矩阵计算方向
		// values[0]: azimuth, rotation around the Z axis.
		// values[1]: pitch, rotation around the X axis.
		// values[2]: roll, rotation around the Y axis.
		SensorManager.getOrientation(rotationMatrix, rotation);
		return (int) (180.0f * rotation[0] / Math.PI);
	}
	
	public float[] getRotationMatrix() {
		float[] rotationMatrix = new float[16];

		// getRotationMatrix(): 计算旋转矩阵
		// 这个旋转矩阵将向量由设备坐标系变换到世界坐标系：
		// X: YZ的向量积，与地面相切，指向东
		// Y: 与地面相切指向北
		// Z: 垂直于地面指向天空
		SensorManager.getRotationMatrix(rotationMatrix, null,
				filteredAcceleration.toFloatArray(), geomagnetic);
		
		return rotationMatrix;
	}
	public float[] getRotationInDeg() {
		float[] rotationMatrix = new float[16];
		float[] rotation = new float[3];

		// getRotationMatrix(): 计算旋转矩阵
		// 这个旋转矩阵将向量由设备坐标系变换到世界坐标系：
		// X: YZ的向量积，与地面相切，指向东
		// Y: 与地面相切指向北
		// Z: 垂直于地面指向天空
		SensorManager.getRotationMatrix(rotationMatrix, null,
				filteredAcceleration.toFloatArray(), geomagnetic);

		// 基于旋转矩阵计算方向
		// values[0]: azimuth, rotation around the Z axis.
		// values[1]: pitch, rotation around the X axis.
		// values[2]: roll, rotation around the Y axis.
		SensorManager.getOrientation(rotationMatrix, rotation);
		float[] degrees = new float[3];
		degrees[0] = (float) (180.0f * rotation[0] / Math.PI);
		degrees[1] = (float) (180.0f * rotation[1] / Math.PI);
		degrees[2] = (float) (180.0f * rotation[2] / Math.PI);
		return degrees;
	}

	public boolean getEkfEnabled() {
		return useEkf;
	}

	public float[] getFilterOutput() {
		float[] rotateMatrix = new float[16];
		double[] glMatrix = ekf.getGLMatrix();
		for (int i = 0; i < 16; i++) {
			rotateMatrix[i] = (float) glMatrix[i];
		}

		// rotateMatrix矩阵绕X轴旋转90度
		Matrix.rotateM(rotateMatrix, 0, 90.0f, 1.0f, 0.0f, 0.0f);

		float[] identityMatrix = new float[16];
		Matrix.setIdentityM(identityMatrix, 0);
		Matrix.rotateM(identityMatrix, 0, imuOrientationDeg, 0.0f, 0.0f, 1.0f);
		Matrix.multiplyMM(tForm, 0, identityMatrix, 0, rotateMatrix, 0);
		
//		Log.d("Sensor", "" + tForm[0] + ", " + tForm[1] + ", " + tForm[2] + ", " + tForm[3]);
//		Log.d("Sensor", "" + tForm[4] + ", " + tForm[5] + ", " + tForm[6] + ", " + tForm[7]);
//		Log.d("Sensor", "" + tForm[8] + ", " + tForm[9] + ", " + tForm[10] + ", " + tForm[11]);
//		Log.d("Sensor", "" + tForm[12] + ", " + tForm[13] + ", " + tForm[14] + ", " + tForm[15]);
//		Log.d("Sensor", "=====================================================================");
		return tForm;
	}

	public Vector3 getFilteredAcceleration() {
		return filteredAcceleration;
	}

	public double getHeadingDegrees() {
		return ekf.getHeadingDegrees();
	}

	public float getImuOrientationDegrees() {
		return imuOrientationDeg;
	}

	public int getNumGyroSamples() {
		return numGyroSamples;
	}

	public boolean isFilteredAccelerationInitialized() {
		return filterInitialized;
	}

	public void resetGyroBias() {
		Arrays.fill(gyroBias, 0.0f);
	}

	public void setGyroBias(float[] gyroBias) {
		this.gyroBias[0] = gyroBias[0];
		this.gyroBias[1] = gyroBias[1];
		this.gyroBias[2] = gyroBias[2];
	}

	public void setHeadingDegrees(double heading) {
		if (heading < 0.0) {
			heading += 360.0;
		}

		if (heading > 360.0) {
			heading -= 360.0;
		}

		ekf.setHeadingDegrees(heading);
	}

	public void setSensorVelocityCallback(Callback<Float> callback) {
		sensorVelocityCallback = callback;
	}

	public void start(Context context) {
		if (Build.VERSION.SDK_INT >= 9) {
			CameraInfo cameraInfo = new CameraInfo();
			Camera.getCameraInfo(0, cameraInfo);
			// 为了让图像按自然方向显示而相机图像需要顺时针旋转的角度
			// 0, 90, 180, 270
			imuOrientationDeg = cameraInfo.orientation;
			Log.d("Sensor", "Model is " + Build.MODEL);
			if (Build.MODEL.startsWith("Nexus 7")) {
				this.imuOrientationDeg = 90.0F;
			}
			Log.d("Sensor", "Camera orientation is : " + cameraInfo.orientation);
		}

		sensorManager = (SensorManager) context
				.getSystemService(Context.SENSOR_SERVICE);
		sensorManager.registerListener(sensorEventListener,
				sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), 1);
		sensorManager.registerListener(sensorEventListener,
				sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), 1);
		sensorManager.registerListener(sensorEventListener,
				sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), 3);
		filterInitialized = false;
		resetGyroBias();
	}
	
	public void stop() {
		if (sensorManager == null) {
			return;
		}
		sensorManager.unregisterListener(sensorEventListener);
	}
}
