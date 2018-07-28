package com.demo.lanscan;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import qiu.niorgai.StatusBarCompat;

public class MainActivity extends AppCompatActivity {

    private RecyclerView rvDevices;

    private DeviceAdapter mAdapter;

    private LANScanner scanner;

    private AppBarLayout appBarLayout;
    private CollapsingToolbarLayout collapsingToolbarLayout;
    private Toolbar toolbar;

    private TextView tvCollapseIp, tvCollapseMac;
    private TextView tvTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        appBarLayout = findViewById(R.id.appbar);
        collapsingToolbarLayout = findViewById(R.id.collapsing);
        toolbar = findViewById(R.id.toolbar);
        tvTitle = findViewById(R.id.tv_title);

        appBarLayout.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                tvTitle.setAlpha(Math.abs(verticalOffset * 1.0f / appBarLayout.getTotalScrollRange()));
            }
        });

        StatusBarCompat.setStatusBarColorForCollapsingToolbar(this, appBarLayout, collapsingToolbarLayout, toolbar, ContextCompat.getColor(this, R.color.colorPrimary));

        tvCollapseIp = findViewById(R.id.tv_collapse_ip);
        tvCollapseMac = findViewById(R.id.tv_collapse_mac);

        tvCollapseIp.setText(LANScanner.getLocalIPAddress());
        tvCollapseMac.setText(LANScanner.getLocalMacAddress(this));

        rvDevices = findViewById(R.id.rv_devices);

//        StaggeredGridLayoutManager manager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
//        manager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
        LinearLayoutManager manager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        rvDevices.setLayoutManager(manager);
        rvDevices.setHasFixedSize(false);
        mAdapter = new DeviceAdapter();
        rvDevices.setAdapter(mAdapter);

        rvDevices.setItemAnimator(new DefaultItemAnimator());

        findViewById(R.id.fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startScan();
            }
        });

        scanner = LANScanner.get();
    }

    private void startScan() {
        mAdapter.clear();
        scanner.scan(new LANScanner.OnScanListener() {
            @Override
            public void onFound(final LANScanner.Device device) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.add(device);
                    }
                });
            }

            @Override
            public void onFinished() {
                Toast.makeText(MainActivity.this, "扫描完成", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public class DeviceAdapter extends RecyclerView.Adapter<DeviceHolder> {

        private final List<LANScanner.Device> mDevices;

        public DeviceAdapter() {
            this.mDevices = Collections.synchronizedList(new ArrayList<LANScanner.Device>());
        }

        public void clear() {
            mDevices.clear();
            notifyDataSetChanged();
        }

        public void add(LANScanner.Device device) {
            mDevices.add(device);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public DeviceHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new DeviceHolder(getLayoutInflater().inflate(R.layout.item_device, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull DeviceHolder holder, int position) {
            holder.tvIp.setText(mDevices.get(position).ip);
            holder.tvMac.setText(mDevices.get(position).mac);
        }

        @Override
        public int getItemCount() {
            return mDevices.size();
        }
    }

    public class DeviceHolder extends RecyclerView.ViewHolder {
        public TextView tvIp;
        public TextView tvMac;

        public DeviceHolder(View itemView) {
            super(itemView);

            tvIp = itemView.findViewById(R.id.tv_ip);
            tvMac = itemView.findViewById(R.id.tv_mac);
        }
    }
}
