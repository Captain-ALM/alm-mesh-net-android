package com.captainalm.mesh.ui.list;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Index;

import com.captainalm.mesh.R;
import com.captainalm.mesh.TheApplication;
import com.captainalm.mesh.db.BaseIDEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides an abstract model for {@link  RecyclerView}.
 *
 * @author Alfred Manville
 */
public abstract class ListAdapter extends RecyclerView.Adapter<ListAdapter.ViewHolder> {

    protected final IListClickHandler clickHandler;
    protected final List<BaseIDEntity> items = new ArrayList<>();
    protected final Context context;
    protected final TheApplication app;

    public ListAdapter(Context context, IListClickHandler cHandler) {
        this.context = context;
        if (context.getApplicationContext() instanceof TheApplication ta)
            app = ta;
        else
            app = null;
        this.clickHandler = getClickHandler(cHandler);
    }

    protected IListClickHandler getClickHandler(IListClickHandler handler) {
        return handler;
    }

    @NonNull
    @Override
    public ListAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(context).inflate(R.layout.list_row, parent, false), clickHandler);
    }

    @Override
    public void onBindViewHolder(@NonNull ListAdapter.ViewHolder holder, int position) {
        holder.idLabel.setText(items.get(position).ID);
        holder.extraLabel.setText(items.get(position).extraData());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public abstract void refresh(boolean reset);

    public String itemIDAt(int index) {
        try {
            return items.get(index).ID;
        } catch (NullPointerException | IndexOutOfBoundsException e) {
            if (app != null)
                app.showException(e);
            return "";
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView idLabel;
        TextView extraLabel;

        public ViewHolder(@NonNull View itemView, IListClickHandler cHandler) {
            super(itemView);
            idLabel = itemView.findViewById(R.id.textViewIDListRow);
            extraLabel = itemView.findViewById(R.id.textViewExtraListRow);
            itemView.setOnClickListener(v -> {
                if (cHandler != null) {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION)
                        cHandler.onItemClicked(pos);
                }
            });
        }
    }
}
