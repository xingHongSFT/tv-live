package com.example.tvlive;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.VH> {

    public interface OnChannelClick {
        void onClick(int position);
    }

    private final Context context;
    private final OnChannelClick click;
    private List<Channel> channels = new ArrayList<>();
    private int selected = -1;

    ChannelAdapter(Context context, List<Channel> channels, OnChannelClick click) {
        this.context = context;
        this.channels = channels;
        this.click = click;
    }

    void setChannels(List<Channel> channels) {
        this.channels = channels;
        notifyDataSetChanged();
    }

    void setSelected(int pos) {
        int old = selected;
        selected = pos;
        if (old >= 0) notifyItemChanged(old);
        notifyItemChanged(pos);
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name;
        TextView group;
        VH(View v) {
            super(v);
            name = v.findViewById(R.id.item_name);
            group = v.findViewById(R.id.item_group);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_channel, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Channel ch = channels.get(position);
        holder.name.setText(ch.name);
        holder.group.setText(ch.group);
        if (position == selected) {
            holder.itemView.setBackgroundResource(R.drawable.bg_selected);
            holder.name.setTextColor(0xFFFFFFFF);
        } else {
            holder.itemView.setBackgroundResource(android.R.color.transparent);
            holder.name.setTextColor(0xFFDDDDDD);
        }
        holder.itemView.setOnClickListener(v -> click.onClick(position));
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }
}
