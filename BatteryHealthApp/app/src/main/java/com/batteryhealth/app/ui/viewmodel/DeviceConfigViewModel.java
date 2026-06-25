package com.batteryhealth.app.ui.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.data.model.DeviceConfig;
import com.batteryhealth.app.data.model.DeviceInfoNode;
import com.batteryhealth.app.data.repository.DeviceRepositoryImpl;
import com.batteryhealth.app.domain.repository.DeviceRepository;
import com.batteryhealth.app.utils.DeviceInfoManager;
import com.batteryhealth.app.utils.DeviceInfoTreeBuilder;
import com.batteryhealth.app.utils.ThreadExecutor;

import android.content.Context;

import java.util.concurrent.atomic.AtomicBoolean;

public class DeviceConfigViewModel extends ViewModel {

    private static final String TAG = "DeviceConfigViewModel";

    private final MutableLiveData<DeviceConfig> deviceConfig = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private final MutableLiveData<Integer> usageDays = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    private final MutableLiveData<DeviceInfoNode> deviceInfoTree = new MutableLiveData<>();
    private final MutableLiveData<DeviceInfoTreeBuilder.DeviceScoreInfo> deviceScore = new MutableLiveData<>();
    private final MutableLiveData<DeviceInfoTreeBuilder.SystemUpdateStatus> systemUpdateStatus = new MutableLiveData<>();
    private final MutableLiveData<String> deviceReportText = new MutableLiveData<>();
    private final MutableLiveData<Boolean> showScoreDetail = new MutableLiveData<>(false);

    private final DeviceRepository deviceRepository;
    private Context appContext;

    private final AtomicBoolean isCleared = new AtomicBoolean(false);

    public DeviceConfigViewModel() {
        BatteryHealthApplication app = BatteryHealthApplication.getInstance();
        if (app == null) {
            android.util.Log.e(TAG, "BatteryHealthApplication instance is null");
            deviceRepository = null;
            return;
        }
        this.appContext = app.getApplicationContext();
        try {
            DeviceInfoManager deviceInfoManager = new DeviceInfoManager(app.getApplicationContext());
            deviceRepository = new DeviceRepositoryImpl(deviceInfoManager);

            deviceInfoManager.setActivationInfoListener(new com.batteryhealth.app.utils.DeviceInfoManager.ActivationInfoListener() {
                @Override
                public void onActivationInfoReady(com.batteryhealth.app.utils.DeviceInfoManager.ActivationInfo activation) {
                    if (activation != null) {
                        usageDays.postValue(activation.usageDays);
                    }
                }
            });
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error initializing DeviceConfigViewModel: " + e.getMessage(), e);
            throw new RuntimeException("Failed to initialize DeviceConfigViewModel", e);
        }
    }

    @Override
    protected void onCleared() {
        isCleared.set(true);
    }

    public LiveData<DeviceConfig> getDeviceConfig() {
        return deviceConfig;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<Integer> getUsageDays() {
        return usageDays;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<DeviceInfoNode> getDeviceInfoTree() {
        return deviceInfoTree;
    }

    public LiveData<DeviceInfoTreeBuilder.DeviceScoreInfo> getDeviceScore() {
        return deviceScore;
    }

    public LiveData<DeviceInfoTreeBuilder.SystemUpdateStatus> getSystemUpdateStatus() {
        return systemUpdateStatus;
    }

    public LiveData<String> getDeviceReportText() {
        return deviceReportText;
    }

    public LiveData<Boolean> getShowScoreDetail() {
        return showScoreDetail;
    }

    public void loadDeviceConfig() {
        if (deviceRepository == null) {
            errorMessage.postValue("初始化失败，请重启应用");
            isLoading.postValue(false);
            return;
        }
        isLoading.postValue(true);
        ThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (isCleared.get()) return;
                try {
                    DeviceConfig config = deviceRepository.getDeviceConfig();
                    deviceConfig.postValue(config);

                    int days = deviceRepository.getUsageDays();
                    usageDays.postValue(days);

                    loadDeviceInfoTreeInternal();
                    loadDeviceScoreInternal();
                    loadSystemUpdateStatusInternal();
                } catch (Exception e) {
                    android.util.Log.e(TAG, "Error loading config: " + e.getMessage(), e);
                    errorMessage.postValue("配置信息加载失败: " + e.getMessage());
                } finally {
                    isLoading.postValue(false);
                }
            }
        });
    }

    public void loadDeviceInfoTree() {
        ThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (isCleared.get()) return;
                loadDeviceInfoTreeInternal();
            }
        });
    }

    private void loadDeviceInfoTreeInternal() {
        if (appContext == null) return;
        try {
            DeviceInfoNode root = DeviceInfoTreeBuilder.buildDeviceInfoTree(appContext);
            deviceInfoTree.postValue(root);
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error building device info tree: " + e.getMessage(), e);
        }
    }

    public void loadDeviceScore() {
        ThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (isCleared.get()) return;
                loadDeviceScoreInternal();
            }
        });
    }

    private void loadDeviceScoreInternal() {
        if (appContext == null) return;
        try {
            DeviceInfoTreeBuilder.DeviceScoreInfo score =
                    DeviceInfoTreeBuilder.calculateDeviceScore(appContext);
            deviceScore.postValue(score);
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error calculating device score: " + e.getMessage(), e);
        }
    }

    public void checkSystemUpdate() {
        ThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (isCleared.get()) return;
                loadSystemUpdateStatusInternal();
            }
        });
    }

    private void loadSystemUpdateStatusInternal() {
        if (appContext == null) return;
        try {
            DeviceInfoTreeBuilder.SystemUpdateStatus status =
                    DeviceInfoTreeBuilder.checkSystemUpdate(appContext);
            systemUpdateStatus.postValue(status);
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error checking system update: " + e.getMessage(), e);
        }
    }

    public void generateDeviceReport() {
        ThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (isCleared.get()) return;
                if (appContext == null) return;
                try {
                    String report = DeviceInfoTreeBuilder.generateDeviceReport(appContext);
                    deviceReportText.postValue(report);
                } catch (Exception e) {
                    android.util.Log.e(TAG, "Error generating device report: " + e.getMessage(), e);
                }
            }
        });
    }

    public void toggleScoreDetail() {
        Boolean current = showScoreDetail.getValue();
        showScoreDetail.postValue(current == null || !current);
    }
}
