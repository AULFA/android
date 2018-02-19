package org.nypl.simplified.app.utilities;

import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;

import com.google.firebase.analytics.FirebaseAnalytics;

/**
 * Created by Skullbonez on 1/14/2018.
 *
 * *** Wraps up Google Analytics for special functions, such as logging events with device ID ***
 */

public final class AnalyticWrapper {

  public static final int FIREBASE_PARAM_LENGTH_LIMIT = 40;

  public static void logEventWithDeviceID(Context context, String eventType, String parameterType, String eventParameter) {

    // Remove any commas from our input, as this is the event delimiter
    if ( eventParameter.contains(",")) {
      eventParameter = eventParameter.replace(",", "");
    }

    // Build the combined event with device ID and the parameter
    String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    String eventPacket = deviceId + "," + eventParameter;

    if ( eventPacket.length() > FIREBASE_PARAM_LENGTH_LIMIT ) {
      // Strings over 40 chars will be IGNORED by Firebase, so send what we can.
      eventPacket = eventPacket.substring(0, FIREBASE_PARAM_LENGTH_LIMIT);
    }

    // Log the event
    final Bundle analyticsData = new Bundle();
    analyticsData.putSerializable(parameterType, eventPacket);
    FirebaseAnalytics.getInstance(context).logEvent(eventType, analyticsData);
  }
}
