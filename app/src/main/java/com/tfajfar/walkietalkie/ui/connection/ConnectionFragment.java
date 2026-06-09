package com.tfajfar.walkietalkie.ui.connection;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import com.tfajfar.walkietalkie.R;

public class ConnectionFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // لایه فرگمنت رو اینجا وصل میکنیم
        return inflater.inflate(R.layout.establish_connection_fragment, container, false);
    }
}