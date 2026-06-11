package com.tfajfar.walkietalkie.ui.guide;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.google.android.material.appbar.MaterialToolbar;
import com.tfajfar.walkietalkie.R;

public class GuideFragment extends Fragment {
@Nullable
@Override
public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle savedInstanceState){
return inflater.inflate(R.layout.fragment_guide,container,false);
}
@Override
public void onViewCreated(@NonNull View view,@Nullable Bundle savedInstanceState){
super.onViewCreated(view,savedInstanceState);
MaterialToolbar toolbar=view.findViewById(R.id.toolbar);
toolbar.setNavigationOnClickListener(v->Navigation.findNavController(v).popBackStack());
}
}
