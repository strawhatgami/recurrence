package com.bleyl.recurrence.utils;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PermissionUtil {

    public interface IPermissionCallback {
        void onPermissionGranted(String[] permissions, int[] grantResults);
    }

    private SparseArray<IPermissionCallback> expectedCallbacks;

    private PermissionUtil() {
        expectedCallbacks = new SparseArray<>();
    }

    private static PermissionUtil INSTANCE = new PermissionUtil();

    public static PermissionUtil getInstance() {
        return INSTANCE;
    }

    public static boolean allGranted(int[] permissionsGranted) {
        int j;

        for (j = 0; j < permissionsGranted.length; j++) {
            if (permissionsGranted[j] != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        return true;
    }

    private int addCallbackToExpectedCallbacks(IPermissionCallback callback) {
        int hashCode = callback.hashCode();
        int requestCode = hashCode & 0xFFFF ; // request code can only use lower 16 bits

        // First, check if collision with an already registered callback
        // Collisions occur when different callbacks (that have thus different hashcodes) share the same requestCode.
        Boolean collision = false;
        for (int i = 0; i < expectedCallbacks.size(); i++) {
            if (requestCode != expectedCallbacks.keyAt(i)) continue;
            collision = collision || hashCode != expectedCallbacks.valueAt(i).hashCode();
        }

        if (collision) {
            Exception e = new Exception("The callback inserted is in collision with another one.");
            e.printStackTrace();
        }

        //finally register the callback
        expectedCallbacks.put(requestCode,callback);

        return requestCode;
    }

    public void allPermissionsGrantedOrAskForThem(Activity activity, String[] permissions, IPermissionCallback callback) {

        List<String> permissionsNeeded = new ArrayList<>();
        for (String permission : permissions){
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
            }
        }

        if (permissionsNeeded.size() == 0) {
            // Permissions are all granted
            int[] grantResults = new int[permissions.length];
            Arrays.fill(grantResults, PackageManager.PERMISSION_GRANTED);
            callback.onPermissionGranted(permissions, grantResults);
            return;
        }

        // Permission is not granted
        // Request the permission, and register the callback
        String[] permsNeeded = permissionsNeeded.toArray(new String[permissionsNeeded.size()]);
        int requestCode = addCallbackToExpectedCallbacks(callback);
        ActivityCompat.requestPermissions(activity, permsNeeded, requestCode);
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        IPermissionCallback callback = expectedCallbacks.get(requestCode);
        if (callback != null) {
            expectedCallbacks.remove(requestCode);
            callback.onPermissionGranted(permissions, grantResults);
        }
    }
}
