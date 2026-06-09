package com.tfajfar.walkietalkie.ui.connection;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.widget.Toast;

public class NetworkHelper {

    public static void checkInternetConnection(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();

        if (activeNetwork == null || !activeNetwork.isConnected()) {
            Toast.makeText(context, "اینترنت خاموش است. لطفاً اینترنت را روشن کنید.", Toast.LENGTH_LONG).show();
        }
    }
}