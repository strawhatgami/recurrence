package com.bleyl.recurrence.utils;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import com.bleyl.recurrence.R;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PermissionUtil {
  /* TODO maybe pass the array ref (R.array.permissionsCallbacksList for now) as argument? */
  private String[] permissionsCallbacks;
  private CallbackSignature[] callbackSignatures;

  private PermissionUtil() {
  }

  private static PermissionUtil INSTANCE = new PermissionUtil();

  public static PermissionUtil getInstance() {
    return INSTANCE;
  }

  public class CallbackSignature {
    private Activity activity;
    private Object callbackObj;
    private String methodName;

    CallbackSignature(Activity activity_, Object callbackObj_, String methodName_){
      activity = activity_;
      callbackObj = callbackObj_;
      methodName = methodName_;
    }

    boolean isRightHandler(Activity handlerActivity){
      return handlerActivity == activity;
    }

    void callCallback(String[] permissions, int[] grantResults){
      if (callbackObj == null) {
        // We don't check that in allPermissionsGrantedOrAskForThem() because registeredCallbackInstance
        // can become null from the time it is stored to the time it is used.
        NullPointerException e = new NullPointerException("Can't find a method on the null object");
        e.printStackTrace();
        return;
      }

      Method callback;
      try {
        callback = callbackObj.getClass().getMethod(methodName, String[].class, int[].class);
      } catch (NoSuchMethodException e) {
        System.err.println("Exception occurred: The callback name passed to allPermissionsGrantedOrAskForThem looks wrong, or the name is right but not the signature.");
        e.printStackTrace();
        return;
      }

      try {
        callback.setAccessible(true);
        callback.invoke(callbackObj, permissions, grantResults);
      } catch (IllegalAccessException e) {
        e.printStackTrace();
      } catch (InvocationTargetException e) {
        System.err.format("Invocation of %s failed: %s%n", methodName, e.getCause().getMessage());
        e.printStackTrace();
      }
    }
  }

  private String duplicateInPermissionsCallbacks() {
    String duplicate;
    int j, k;

    for (j = 0; j < permissionsCallbacks.length; j++) {
      for (k = j + 1; k < permissionsCallbacks.length; k++) {
        if (k != j && permissionsCallbacks[k].equals(permissionsCallbacks[j])) {
          duplicate = permissionsCallbacks[k];
          return duplicate;
        }
      }
    }

    return null;
  }

  private void initInternalVarsIfNeeded(Activity activity) {
    permissionsCallbacks = activity.getResources().getStringArray(R.array.permissionsCallbacksList);
    callbackSignatures = new CallbackSignature[permissionsCallbacks.length];

    String duplicate = duplicateInPermissionsCallbacks();
    if (duplicate != null) {
      Exception e = new Exception("Callbacks names must be unique in xml file. Duplicate found: " + duplicate);
      e.printStackTrace();
    }
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

  public void allPermissionsGrantedOrAskForThem(Activity activity, Object callbackInstance, int callbackNameRef, String[] permissions) {
    initInternalVarsIfNeeded(activity);

    List<String> permissionsNeeded = new ArrayList<>();
    for (String permission : permissions){
      if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
        permissionsNeeded.add(permission);
      }
    }

    String callbackName = activity.getResources().getString(callbackNameRef);
    CallbackSignature callbackSignature = new CallbackSignature(activity, callbackInstance, callbackName);

    if (permissionsNeeded.size() == 0) {
      // Permissions are all granted
      int[] grantResults = new int[permissions.length];
      Arrays.fill(grantResults, PackageManager.PERMISSION_GRANTED);
      callbackSignature.callCallback(permissions, grantResults);
      return;
    }

    // Permission is not granted
    // Request the permission, and register the callback
    String[] permsNeeded = permissionsNeeded.toArray(new String[permissionsNeeded.size()]);
    int indexId = Arrays.asList(permissionsCallbacks).indexOf(callbackName);
    callbackSignatures[indexId] = callbackSignature;
    ActivityCompat.requestPermissions(activity, permsNeeded, indexId);
  }

  public void allPermissionsGrantedOrAskForThem(Activity activity, int callbackNameRef, String[] permissions) {
    allPermissionsGrantedOrAskForThem(activity, activity, callbackNameRef, permissions);
  }

  public void onRequestPermissionsResult(Activity activity, int requestCode, String[] permissions, int[] grantResults) {
    // test if it is the right activity to handle that request
    CallbackSignature callbackSignature = callbackSignatures[requestCode];
    if (!callbackSignature.isRightHandler(activity)) return;

    callbackSignature.callCallback(permissions, grantResults);
  }
}
