package com.samboluong.imageloader.adapter;

import android.content.Context;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.samboluong.imageloader.R;
import com.samboluong.imageloader.util.ImageLoader;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ImageAdapter extends BaseAdapter {

    private static Set<String> mSelected = new HashSet<>();

    private String mDirPath;
    private List<String> mImgNameList;
    private LayoutInflater mInflater;

    private int mScreenWidth;


    public ImageAdapter(Context context, List<String> imgNameList, String dirPath) {
        this.mDirPath = dirPath;
        this.mImgNameList = imgNameList;
        this.mInflater = LayoutInflater.from(context);

        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics outMetrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(outMetrics);
        mScreenWidth = outMetrics.widthPixels;
    }

    @Override
    public int getCount() {
        return mImgNameList.size();
    }

    @Override
    public Object getItem(int position) {
        return mImgNameList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        final ViewHolder holder;
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.item_gridview, parent, false);
            holder = new ViewHolder();
            holder.imageView = (ImageView) convertView.findViewById(R.id.id_item_image);
            holder.selectButton = (ImageButton) convertView.findViewById(R.id.id_item_select);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        // 重置,因为后面的页面会复用控件，如果不重置在图片加载完成之前会显示以前的图片
        holder.imageView.setImageResource(R.drawable.pictures_no);
        holder.imageView.setImageResource(R.drawable.picture_unselected);
        holder.imageView.setColorFilter(null);

        holder.imageView.setMaxWidth(mScreenWidth / 3);

        // 使用工具类,根据全路径为ImageView设置图片
        ImageLoader.getInstance(3, ImageLoader.Type.LIFO)
                .loadImage(mDirPath + "/" + mImgNameList.get(position), holder.imageView);

        final String filePath = mDirPath + "/" + mImgNameList.get(position);

        holder.imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSelected.contains(filePath)) {
                    mSelected.remove(filePath);
                    holder.imageView.setColorFilter(null);
                    holder.selectButton.setImageResource(R.drawable.picture_unselected);
                } else {
                    mSelected.add(filePath);
                    holder.imageView.setColorFilter(Color.parseColor("#77000000"));
                    holder.selectButton.setImageResource(R.drawable.pictures_selected);
                }
//                notifyDataSetChanged();
            }
        });

        if (mSelected.contains(filePath)) {
            holder.imageView.setColorFilter(Color.parseColor("#77000000"));
            holder.selectButton.setImageResource(R.drawable.pictures_selected);
        }


        return convertView;
    }

    private class ViewHolder {
        ImageView imageView;
        ImageButton selectButton;
    }

}
