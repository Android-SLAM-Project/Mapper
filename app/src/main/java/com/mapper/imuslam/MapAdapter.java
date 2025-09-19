package com.mapper.imuslam;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
public class MapAdapter extends ArrayAdapter<MyMapItem> {
    private Context context;
    private List<MyMapItem> mapItems;

    public MapAdapter(@NonNull Context context, @NonNull List<MyMapItem> mapItems) {
        super(context, 0, mapItems);
        this.context = context;
        this.mapItems = mapItems;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_map, parent, false);
        }

        MyMapItem currentItem = mapItems.get(position);

        ImageView imageView = convertView.findViewById(R.id.item_image);
        TextView titleView = convertView.findViewById(R.id.item_title);
        TextView subtitleView = convertView.findViewById(R.id.item_subtitle);

        // Bind data
        Bitmap bmp = currentItem.getThumbnail();
        if (bmp != null) {
            imageView.setImageBitmap(bmp);
        } else {
            imageView.setImageResource(R.drawable.map_icon_cd); // fallback drawable in res/drawable
        }
        titleView.setText(currentItem.getTitle());
        subtitleView.setText(currentItem.getSubtitle());

        return convertView;
    }
}
