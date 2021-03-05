package com.handapp.mediapipebluetooth;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.mediapipe.components.CameraHelper;
import com.google.mediapipe.components.CameraXPreviewHelper;
import com.google.mediapipe.components.ExternalTextureConverter;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.framework.AndroidPacketCreator;
import com.google.mediapipe.framework.Packet;
import com.google.mediapipe.framework.PacketGetter;
import com.google.mediapipe.glutil.EglManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mikera.vectorz.Vector2;
import mikera.vectorz.Vector3;
import com.handapp.mediapipebluetooth.FingerAngles;
import com.handapp.mediapipebluetooth.FingerCircles;
/**
 * Main activity of MediaPipe example apps.
 */
public class MediapipeFragment extends Fragment {
    private static final String TAG = "MainActivity";
    private static final String BINARY_GRAPH_NAME = "hand_tracking_mobile_gpu.binarypb";
    private static final String INPUT_VIDEO_STREAM_NAME = "input_video";
    private static final String OUTPUT_VIDEO_STREAM_NAME = "output_video";
    private static final String OUTPUT_LANDMARKS_STREAM_NAME = "hand_landmarks";
    private static final String INPUT_NUM_HANDS_SIDE_PACKET_NAME = "num_hands";
    private static final int NUM_HANDS = 1;
    private static final CameraHelper.CameraFacing CAMERA_FACING = CameraHelper.CameraFacing.FRONT;
    // Flips the camera-preview frames vertically before sending them into FrameProcessor to be
    // processed in a MediaPipe graph, and flips the processed frames back when they are displayed.
    // This is needed because OpenGL represents images assuming the image origin is at the bottom-left
    // corner, whereas MediaPipe in general assumes the image origin is at top-left.
    private static final boolean FLIP_FRAMES_VERTICALLY = true;

    static {
        // Load all native libraries needed by the app.
        System.loadLibrary("mediapipe_jni");
        System.loadLibrary("opencv_java3");
    }

    // {@link SurfaceTexture} where the camera-preview frames can be accessed.
    private SurfaceTexture previewFrameTexture;
    // {@link SurfaceView} that displays the camera-preview frames processed by a MediaPipe graph.
    private SurfaceView previewDisplayView;
    // Creates and manages an {@link EGLContext}.
    private EglManager eglManager;
    // Sends camera-preview frames into a MediaPipe graph for processing, and displays the processed
    // frames onto a {@link Surface}.
    private FrameProcessor processor;
    // Converts the GL_TEXTURE_EXTERNAL_OES texture from Android camera into a regular texture to be
    // consumed by {@link FrameProcessor} and the underlying MediaPipe graph.
    private ExternalTextureConverter converter;
    // ApplicationInfo for retrieving metadata defined in the manifest.
    private ApplicationInfo applicationInfo;
    // Handles camera access via the {@link CameraX} Jetpack support library.
    private CameraXPreviewHelper cameraHelper;
    //The stop and record toggle button for sending hand values over bluetooth.
    private Button btnSend;
    //The context from the inflater
    private Context context;
    private boolean timerRunning;
    private ToggleButton toggleHand;
    public static boolean isHandLeft;

    int counter;

    public static MediapipeFragment newInstance() {
        return new MediapipeFragment();
    }

    public interface MediapipeInterface {
        void sendDataFromMedipipe(String data);
    }

    MediapipeInterface mediapipeInterface;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        context = inflater.getContext();
        View view = inflater.inflate(R.layout.mediapipe_fragment, container, false);
        previewDisplayView = new SurfaceView(context);

        try {
            applicationInfo =
                    getActivity().getPackageManager().getApplicationInfo(getActivity().getPackageName(), PackageManager.GET_META_DATA);
            initMediapipe();
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Cannot find application info: " + e);
        }

        setupPreviewDisplayView(view);
        toggleHand = view.findViewById(R.id.switchHandedness);

        toggleHand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!toggleHand.isChecked()) {
                    isHandLeft = true;
                    System.out.println("isHandLeft: " + isHandLeft);
                } else if (toggleHand.isChecked()){
                    isHandLeft = false;
                    System.out.println("isHandLeft: " + isHandLeft);
                }
            }
        });

        return view;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Activity activity = (Activity) context;

        try {
            mediapipeInterface = (MediapipeInterface) activity;
        } catch(RuntimeException a) {
            throw new RuntimeException((activity.toString() + "Must implement Method"));
        }
    }

    private void initMediapipe() {
        // Initialize asset manager so that MediaPipe native libraries can access the app assets, e.g.,
        // binary graphs.
        AndroidAssetUtil.initializeNativeAssetManager(context);
        eglManager = new EglManager(null);
        processor =
                new FrameProcessor(
                        getContext(),
                        eglManager.getNativeContext(),
                        BINARY_GRAPH_NAME,
                        INPUT_VIDEO_STREAM_NAME,
                        OUTPUT_VIDEO_STREAM_NAME);
        processor
                .getVideoSurfaceOutput()
                .setFlipY(FLIP_FRAMES_VERTICALLY);

        PermissionHelper.checkAndRequestCameraPermissions(getActivity());
        AndroidPacketCreator packetCreator = processor.getPacketCreator();
        Map<String, Packet> inputSidePackets = new HashMap<>();
        inputSidePackets.put(INPUT_NUM_HANDS_SIDE_PACKET_NAME, packetCreator.createInt32(NUM_HANDS));
        processor.setInputSidePackets(inputSidePackets);

        processor.addPacketCallback(
                OUTPUT_LANDMARKS_STREAM_NAME,
                (packet) -> {
                    List<NormalizedLandmarkList> multiHandLandmarks =
                            PacketGetter.getProtoVector(packet, NormalizedLandmarkList.parser());
                    sendDataToBluetooth(multiHandLandmarks);
                });
    }

    public void sendDataToBluetooth(List<NormalizedLandmarkList> multiHandLandmarks) {
        counter +=1;

        if (counter > 1) {
            if (timerRunning) {
                String data = getAnglesOfFingersString(multiHandLandmarks);
                mediapipeInterface.sendDataFromMedipipe(data);
                Log.i(TAG, "" + getAnglesOfFingersString(multiHandLandmarks));
            } else {
                return;
            }
            counter = 0;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        converter =
                new ExternalTextureConverter(
                        eglManager.getContext(), 2);
        converter.setFlipY(FLIP_FRAMES_VERTICALLY);
        converter.setConsumer(processor);
        if (PermissionHelper.cameraPermissionsGranted((Activity) context)) {
            startCamera();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        converter.close();

        // Hide preview display until we re-open the camera again.
        previewDisplayView.setVisibility(View.GONE);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    protected void onCameraStarted(SurfaceTexture surfaceTexture) {
        previewFrameTexture = surfaceTexture;
        // Make the display view visible to start showing the preview. This triggers the
        // SurfaceHolder.Callback added to (the holder of) previewDisplayView.
        previewDisplayView.setVisibility(View.VISIBLE);
    }

    protected Size cameraTargetResolution() {
        return null; // No preference and let the camera (helper) decide.
    }

    public void startCamera() {
        cameraHelper = new CameraXPreviewHelper();
        cameraHelper.setOnCameraStartedListener(
                surfaceTexture -> {
                    onCameraStarted(surfaceTexture);
                });
        CameraHelper.CameraFacing cameraFacing = CameraHelper.CameraFacing.FRONT;
        cameraHelper.startCamera(
                context, this, /*unusedSurfaceTexture=*/ cameraFacing, cameraTargetResolution());
    }

    protected Size computeViewSize(int width, int height) {
        return new Size(width, height);
    }

    protected void onPreviewDisplaySurfaceChanged(
            SurfaceHolder holder, int format, int width, int height) {
        // (Re-)Compute the ideal size of the camera-preview display (the area that the
        // camera-preview frames get rendered onto, potentially with scaling and rotation)
        // based on the size of the SurfaceView that contains the display.
        Size viewSize = computeViewSize(width, height);
        Size displaySize = cameraHelper.computeDisplaySizeFromViewSize(viewSize);
        boolean isCameraRotated = cameraHelper.isCameraRotated();

        // Connect the converter to the camera-preview frames as its input (via
        // previewFrameTexture), and configure the output width and height as the computed
        // display size.
        converter.setSurfaceTextureAndAttachToGLContext(
                previewFrameTexture,
                isCameraRotated ? displaySize.getHeight() : displaySize.getWidth(),
                isCameraRotated ? displaySize.getWidth() : displaySize.getHeight());
    }

    private void setupPreviewDisplayView(View view) {
        previewDisplayView.setVisibility(View.GONE);
        ViewGroup viewGroup = view.findViewById(R.id.preview_display_layout);
        viewGroup.addView(previewDisplayView);

        previewDisplayView
                .getHolder()
                .addCallback(
                        new SurfaceHolder.Callback() {
                            @Override
                            public void surfaceCreated(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(holder.getSurface());
                            }

                            @Override
                            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                                onPreviewDisplaySurfaceChanged(holder, format, width, height);
                            }

                            @Override
                            public void surfaceDestroyed(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(null);
                            }
                        });
    }

    public void receiveCountDownState(boolean isTimerRunning) {
        timerRunning = isTimerRunning;
    }

    private String getMultiHandLandmarksDebugString(List<NormalizedLandmarkList> multiHandLandmarks) {
        if (multiHandLandmarks.isEmpty()) {
            return "No hand landmarks";
        }
        String multiHandLandmarksStr = "";
        int handIndex = 0;
        for (NormalizedLandmarkList landmarks : multiHandLandmarks) {
            multiHandLandmarksStr +=
                    "\t#Hand landmarks for hand[" + handIndex + "]: " + landmarks.getLandmarkCount() + "\n";
            int landmarkIndex = 0;
            for (NormalizedLandmark landmark : landmarks.getLandmarkList()) {

                if (landmarkIndex == 8) {
                    multiHandLandmarksStr +=
                            "\t\tLandmark ["
                                    + landmarkIndex
                                    + "]: ("
                                    + landmark.getX()
                                    + ", "
                                    + landmark.getY()
                                    + ", "
                                    + landmark.getZ()
                                    + ")\n";
                }
                ++landmarkIndex;
            }
            ++handIndex;
        }
        return multiHandLandmarksStr;
    }

    private String getAnglesOfFingersString(List<NormalizedLandmarkList> multiHandLandmarks) {
        if (multiHandLandmarks.isEmpty()) {
            return "No hand landmarks";
        }
        String fingerValuesString = null;
        int handIndex = 0;

        Vector3 palm0 = null;
        Vector3 palm5 = null;
        Vector3 palm13 = null;
        Vector3 palm17 = null;;

        Vector3 thumb1 = null;
        Vector3 thumb2 = null;
        Vector3 index1 = null;
        Vector3 index2 = null;
        Vector3 mid1 = null;
        Vector3 mid2 = null;
        Vector3 ring1 = null;
        Vector3 ring2 = null;

        for (NormalizedLandmarkList landmarks : multiHandLandmarks)  {
            int landmarkIndex = 0;
            for (NormalizedLandmark landmark : landmarks.getLandmarkList()) {
                if (landmarkIndex == 0) {
                    palm0 = Vector3.of(landmark.getX(), landmark.getY(), landmark.getZ());
                }

                if (landmarkIndex == 1) {
                    thumb1 = Vector3.of(landmark.getX(), landmark.getY(), landmark.getZ());
                }

                if (landmarkIndex == 4) {
                    thumb2 = Vector3.of(landmark.getX(), landmark.getY(), landmark.getZ());
                }

                if (landmarkIndex == 5) {
                    palm5 = Vector3.of(landmark.getX(), landmark.getY(), landmark.getZ());
                }

                if (landmarkIndex == 5) {
                    index1 = Vector3.of(landmark.getX(), landmark.getY(), landmark.getZ());
                }

                if (landmarkIndex == 8) {
                    index2 = Vector3.of(landmark.getX(), landmark.getY(), landmark.getZ());
                }

                if (landmarkIndex == 9) {
                    mid1 = Vector3.of(landmark.getX(), landmark.getY(), landmark.getZ());
                }

                if (landmarkIndex == 12) {
                    mid2 = Vector3.of(landmark.getX(), landmark.getY(), landmark.getZ());
                }

                if (landmarkIndex == 13) {
                    ring1 = Vector3.of(landmark.getX(), landmark.getY(), landmark.getZ());
                }

                if (landmarkIndex == 13) {
                    palm13 = Vector3.of(landmark.getX(), landmark.getY(), landmark.getZ());
                }

                if (landmarkIndex == 16) {
                    ring2 = Vector3.of(landmark.getX(), landmark.getY(), landmark.getZ());
                }

                if (landmarkIndex == 17) {
                    palm17 = Vector3.of(landmark.getX(), landmark.getY(), landmark.getZ());
                }

                ++landmarkIndex;
            }

            Vector3 palmNormal = FingerAngles.getNormal(palm0, palm5, palm17);
            Vector3 thumbNormal = FingerAngles.getThumbNormal(palm0, palm13, palm17, palm5); //0-> 13 and 17-> 5

            double thumbAngle = FingerAngles.servoAngle(FingerAngles.fingerDir(thumb1, thumb2), thumbNormal, true);
            double indexAngle = FingerAngles.servoAngle(FingerAngles.fingerDir(index1, index2), palmNormal, false);
            double midAngle = FingerAngles.servoAngle(FingerAngles.fingerDir(mid1, mid2), palmNormal, false);
            double ringAngle = FingerAngles.servoAngle(FingerAngles.fingerDir(ring1, ring2), palmNormal, false);

            fingerValuesString = (int)thumbAngle + "," + (int)indexAngle + "," + (int)midAngle + "," + (int)ringAngle;
            ++handIndex;
        }
        return fingerValuesString;
    }

    private String getCirclesOfFingersString(List<NormalizedLandmarkList> multiHandLandmarks) {
        if (multiHandLandmarks.isEmpty()) {
            return "No hand landmarks";
        }
        String fingerCirclesString = null;
        int handIndex = 0;

        Vector3 thumb1 = null, thumb2 = null, thumb3 = null;
        Vector3 index1 = null, index2 = null, index3 = null;
        Vector3 mid1 = null, mid2 = null, mid3 = null;
        Vector3 ring1 = null, ring2 = null, ring3 = null;

        for (NormalizedLandmarkList landmarks : multiHandLandmarks)  {
            int landmarkIndex = 0;
            for (NormalizedLandmark landmark : landmarks.getLandmarkList()) {

                if (landmarkIndex == 2) {
                    thumb1 = Vector3.of(landmark.getX(), landmark.getY(), landmark.getZ());
                }

                if (landmarkIndex == 3) {
                    thumb2 = Vector3.of(landmark.getX(), landmark.getY(), landmark.getZ());
                }

                if (landmarkIndex == 4) {
                    thumb3 = Vector3.of(landmark.getX(), landmark.getY(), landmark.getZ());
                }

                if (landmarkIndex == 6) {
                    index1 = Vector3.of(landmark.getX(), landmark.getY(), landmark.getZ());
                }

                if (landmarkIndex == 7) {
                    index2 = Vector3.of(landmark.getX(), landmark.getY(), landmark.getZ());
                }

                if (landmarkIndex == 8) {
                    index3 = Vector3.of(landmark.getX(), landmark.getY(), landmark.getZ());
                }

                if (landmarkIndex == 10) {
                    mid1 = Vector3.of(landmark.getX(), landmark.getY(), landmark.getZ());
                }

                if (landmarkIndex == 11) {
                    mid2 = Vector3.of(landmark.getX(), landmark.getY(), landmark.getZ());
                }

                if (landmarkIndex == 12) {
                    mid3 = Vector3.of(landmark.getX(), landmark.getY(), landmark.getZ());
                }

                if (landmarkIndex == 14) {
                    ring1 = Vector3.of(landmark.getX(), landmark.getY(), landmark.getZ());
                }

                if (landmarkIndex == 15) {
                    ring2 = Vector3.of(landmark.getX(), landmark.getY(), landmark.getZ());
                }

                if (landmarkIndex == 16) {
                    ring3 = Vector3.of(landmark.getX(), landmark.getY(), landmark.getZ());
                }

                ++landmarkIndex;
            }

            List rotatedThumb = FingerCircles.rotatePoints(thumb1, thumb2, thumb3);
            Vector3[] rotatedPointsThumb = (Vector3[])rotatedThumb.get(1);
            float thumbAngle = FingerCircles.getAngle(rotatedPointsThumb[0], rotatedPointsThumb[1], rotatedPointsThumb[2], true);

            List rotatedI = FingerCircles.rotatePoints(index1, index2, index3);
            Vector3[] rotatedPointsI = (Vector3[])rotatedI.get(1);
            float indexAngle = FingerCircles.getAngle(rotatedPointsI[0], rotatedPointsI[1], rotatedPointsI[2], true);

            List rotatedM = FingerCircles.rotatePoints(mid1, mid2, mid3);
            Vector3[] rotatedPointsM = (Vector3[])rotatedM.get(1);
            float midAngle = FingerCircles.getAngle(rotatedPointsM[0], rotatedPointsM[1], rotatedPointsM[2], true);

            List rotatedR = FingerCircles.rotatePoints(ring1, ring2, ring3);
            Vector3[] rotatedPointsR = (Vector3[])rotatedR.get(1);
            float ringAngle = FingerCircles.getAngle(rotatedPointsR[0], rotatedPointsR[1], rotatedPointsR[2], true);

            fingerCirclesString = (int)thumbAngle + "," + (int)indexAngle + "," + (int)midAngle + "," + (int)ringAngle;
            System.out.println(fingerCirclesString);
            ++handIndex;
        }
        return fingerCirclesString;
    }
}