package com.mapper.imuslam;

import android.os.Bundle;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SavedMap extends Fragment {

    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private String mParam1;
    private String mParam2;
    private ListView mapsList;

    public SavedMap() {
        // Required empty public constructor
    }

    public static SavedMap newInstance(String param1, String param2) {
        SavedMap fragment = new SavedMap();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_saved_map, container, false);
        mapsList = view.findViewById(R.id.map_list);

        // Read from the "SLAM_MAPS" directory.
        File mapsDir = new File(requireContext().getExternalFilesDir(null), "SLAM_MAPS/");
        List<String> mapNames = new ArrayList<>();

        if (mapsDir.exists() && mapsDir.isDirectory()) {
            File[] folders = mapsDir.listFiles(File::isDirectory);
            if (folders != null) {
                for (File folder : folders) {
                    mapNames.add(folder.getName());
                }
            }
        } else {
            Toast.makeText(requireContext(), "SLAM_MAPS folder not found", Toast.LENGTH_SHORT).show();
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, mapNames);
        mapsList.setAdapter(adapter);

        mapsList.setOnItemClickListener((parent, view1, position, id) -> {
            String selectedFolderName = mapNames.get(position);

            File selectedFolder = new File(mapsDir, selectedFolderName);
            // Log the selected folder absolute path for diagnostics.
//            Toast.makeText(requireContext(), "Opening folder: " + selectedFolder.getAbsolutePath(), Toast.LENGTH_SHORT).show();

            SingleMap singleMapFragment = SingleMap.newInstance(selectedFolder.getAbsolutePath());
            FragmentTransaction transaction = requireActivity().getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.container, singleMapFragment);
            transaction.addToBackStack(null);
            transaction.commit();
        });

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                new AlertDialog.Builder(getContext())
                        .setMessage("Are you sure you want to exit?")
                        .setPositiveButton("Yes", (dialog, which) -> requireActivity().finish())
                        .setNegativeButton("No", null)
                        .show();
            }
        });
        return view;
    }
}
