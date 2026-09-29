package com.eyeguard.app.feature.filter

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.eyeguard.app.core.datastore.SettingsRepository
import com.eyeguard.app.core.service.EyeGuardService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 快速设置（下拉快捷开关）Tile：一键开关护眼遮罩。
 *
 * 点击 → 翻转 DataStore 中的 filterEnabled 并确保 EyeGuardService 在运行
 * （遮罩的实际显示 / 隐藏由服务每轮循环根据设置应用，见 EyeGuardService）。
 */
@AndroidEntryPoint
class EyeFilterTileService : TileService() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val enabled = settingsRepository.filterEnabled.first()
            updateTile(enabled)
        }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val current = settingsRepository.filterEnabled.first()
            settingsRepository.setFilterEnabled(!current)
            // 服务未运行时启动它，保证遮罩状态被应用
            EyeGuardService.start(this@EyeFilterTileService)
            updateTile(!current)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun updateTile(enabled: Boolean) {
        qsTile?.apply {
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(com.eyeguard.app.R.string.tile_label)
            updateTile()
        }
    }
}
