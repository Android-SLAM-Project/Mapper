package com.mapper.imuslam;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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
        List<MyMapItem> mapItems = new ArrayList<>();
        if (mapsDir.exists() && mapsDir.isDirectory()) {
            File[] folders = mapsDir.listFiles(File::isDirectory);
            if (folders != null) {



                for (File folder : folders) {
                    String folderName = folder.getName();
                    mapNames.add(folderName);
                    String title = folderName;
                    String subtitle = "";
                    long time = parseFolderTime(folderName);
                    try {
                        String[] parts = folderName.split("_");
                        // defensive check: parts length >= 7 -> ["SLAM","Map","yyyy","mm","dd","hh","mm","ss"] maybe 8
                        if (parts.length >= 8) {
                            String yyyy = parts[2];
                            String mm = parts[3];
                            String dd = parts[4];
                            String hh = parts[5];
                            String min = parts[6];
                            String ss = parts.length > 7 ? parts[7] : "00";
                            subtitle = "Date : " + yyyy + "-" + mm + "-" + dd + "    Time : " + hh + ":" + min + ":" + ss;
                        } else {
                            // fallback: if format differs, show raw folder name as subtitle
                            subtitle = folderName;
                        }
                    } catch (Exception e) {
                        subtitle = folderName;
                    }

                    File[] files = folder.listFiles();
                    File mergedMapFile = null;
                    if (files != null) {
                        for (File f : files) {
                            String name = f.getName().toLowerCase(Locale.getDefault());
                            if (name.contains("mapphoto") && (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png"))) {
                                mergedMapFile = f;
                                break;
                            }
                        }
                    }
                    Bitmap thumb = null;
                    if (mergedMapFile != null && mergedMapFile.exists()) {
                        try {
                            // target thumbnail size (pixels)
                            final int TARGET_SZ = 200;

                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inJustDecodeBounds = true;
                            BitmapFactory.decodeFile(mergedMapFile.getAbsolutePath(), options);
                            int srcW = options.outWidth;
                            int srcH = options.outHeight;

                            int inSampleSize = 1;
                            if (srcH > TARGET_SZ || srcW > TARGET_SZ) {
                                final int halfH = srcH / 2;
                                final int halfW = srcW / 2;
                                while ((halfH / inSampleSize) >= TARGET_SZ && (halfW / inSampleSize) >= TARGET_SZ) {
                                    inSampleSize *= 2;
                                }
                            }
                            options.inSampleSize = inSampleSize;
                            options.inJustDecodeBounds = false;
                            options.inPreferredConfig = Bitmap.Config.RGB_565; // memory friendly
                            thumb = BitmapFactory.decodeFile(mergedMapFile.getAbsolutePath(), options);
                        } catch (Exception e) {
                            Log.e("MapList", "Failed to decode thumbnail for " + folderName, e);
                        }
                    }

                    // create and add item

                    mapItems.add(new MyMapItem(thumb, title, subtitle,time));

                }
            }
        } else {
            Toast.makeText(requireContext(), "SLAM_MAPS folder not found", Toast.LENGTH_SHORT).show();
        }
        Collections.sort(mapItems, (a, b) -> Long.compare(b.timestamp, a.timestamp));
        MapAdapter Mapadapter = new MapAdapter(requireContext(), mapItems);
        //ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, mapNames);
        mapsList.setAdapter(Mapadapter);

        mapsList.setOnItemClickListener((parent, view1, position, id) -> {
            MyMapItem clickedItem = mapItems.get(position);
            String selectedFolderName = clickedItem.getTitle();// title holds folder name
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
    private long parseFolderTime(String folderName) {
        try {
            String[] p = folderName.split("_");
            if (p.length >= 8) {
                String dt = p[2] + "-" + p[3] + "-" + p[4] + " "
                        + p[5] + ":" + p[6] + ":" + p[7];
                SimpleDateFormat sdf =
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                return sdf.parse(dt).getTime();
            }
        } catch (Exception ignore) {}
        return 0L;
    }

}
