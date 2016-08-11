package com.samboluong.imageloader.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.samboluong.imageloader.R;
import com.samboluong.imageloader.bean.FolderBean;
import com.samboluong.imageloader.util.ImageLoader;

import java.util.List;

public class ListDirAdapter extends ArrayAdapter<FolderBean> {

    private LayoutInflater mInflater;
    private List<FolderBean> mDataList;

    public ListDirAdapter(Context context, List<FolderBean> objects) {
        super(context, 0, objects);
        mInflater = LayoutInflater.from(context);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.item_popup_main, parent, false);
            holder = new ViewHolder();
            holder.mImageView = (ImageView) convertView.findViewById(R.id.id_dir_item_image);
            holder.mDirName = (TextView) convertView.findViewById(R.id.id_dir_item_name);
            holder.mDirCount = (TextView) convertView.findViewById(R.id.id_dir_item_count);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        FolderBean bean = getItem(position);
        holder.mImageView.setImageResource(R.drawable.pictures_no); // 重置
        ImageLoader.getInstance().loadImage(bean.getFirstImagePath(), holder.mImageView);
        holder.mDirName.setText(bean.getName());
        holder.mDirCount.setText(bean.getCount() + "张");

        return convertView;
    }

    private class ViewHolder {
        ImageView mImageView;
        TextView mDirName;
        TextView mDirCount;
    }
}
