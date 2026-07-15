package io.legado.app.ui.main.explore.manage

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.ExploreContainer
import io.legado.app.help.source.ExploreContainerHelp
import io.legado.app.ui.widget.dialog.GroupManageDialog

class ExploreManageViewModel(application: Application) : BaseViewModel(application),
    GroupManageDialog.GroupOps {

    fun update(vararg container: ExploreContainer) {
        execute { appDb.exploreContainerDao.update(*container) }
    }

    fun delete(container: ExploreContainer) {
        execute {
            appDb.exploreContainerDao.delete(container)
            ExploreContainerHelp.removeCache(container.id)
        }
    }

    /** 批量删除:先删行后逐 id 清缓存(与写后校验的顺序契约一致) */
    fun deleteSelection(containers: List<ExploreContainer>) {
        execute {
            appDb.exploreContainerDao.delete(*containers.toTypedArray())
            containers.forEach { ExploreContainerHelp.removeCache(it.id) }
        }
    }

    fun toTop(container: ExploreContainer) {
        execute {
            container.sortOrder = appDb.exploreContainerDao.minOrder - 1
            appDb.exploreContainerDao.update(container)
        }
    }

    fun toBottom(container: ExploreContainer) {
        execute {
            container.sortOrder = appDb.exploreContainerDao.maxOrder + 1
            appDb.exploreContainerDao.update(container)
        }
    }

    fun upOrder() {
        execute {
            val containers = appDb.exploreContainerDao.all
            for ((index, container) in containers.withIndex()) {
                container.sortOrder = index + 1
            }
            appDb.exploreContainerDao.update(*containers.toTypedArray())
        }
    }

    fun enableAll(enable: Boolean) {
        execute {
            val containers = appDb.exploreContainerDao.all
            containers.forEach { it.enabled = enable }
            appDb.exploreContainerDao.update(*containers.toTypedArray())
        }
    }

    fun enableSelection(containers: List<ExploreContainer>, enable: Boolean) {
        execute {
            containers.forEach { it.enabled = enable }
            appDb.exploreContainerDao.update(*containers.toTypedArray())
        }
    }

    /** 批量加入分组(copy 语义,不改 adapter 引用) */
    fun selectionAddToGroups(containers: List<ExploreContainer>, groups: String) {
        execute {
            val array = Array(containers.size) {
                containers[it].copy().apply { addGroup(groups) }
            }
            appDb.exploreContainerDao.update(*array)
        }
    }

    /** 批量移出分组(copy 语义) */
    fun selectionRemoveFromGroups(containers: List<ExploreContainer>, groups: String) {
        execute {
            val array = Array(containers.size) {
                containers[it].copy().apply { removeGroup(groups) }
            }
            appDb.exploreContainerDao.update(*array)
        }
    }

    /** 分组管理·添加:无分组容器全部归入该组(书源家族语义) */
    override fun addGroup(group: String) {
        execute {
            val containers = appDb.exploreContainerDao.noGroup
            containers.forEach { it.addGroup(group) }
            appDb.exploreContainerDao.update(*containers.toTypedArray())
        }
    }

    /** 分组管理·改名:new 为空即仅移除;其余分组保留 */
    override fun upGroup(oldGroup: String, newGroup: String?) {
        execute {
            val containers = appDb.exploreContainerDao.getByGroup(oldGroup)
                .filter { it.hasGroup(oldGroup) }
            containers.forEach {
                it.removeGroup(oldGroup)
                if (!newGroup.isNullOrEmpty()) it.addGroup(newGroup)
            }
            appDb.exploreContainerDao.update(*containers.toTypedArray())
        }
    }

    /** 分组管理·删除:全表移除该组,容器其余分组保留 */
    override fun delGroup(group: String) {
        execute {
            val containers = appDb.exploreContainerDao.getByGroup(group)
            containers.forEach { it.removeGroup(group) }
            appDb.exploreContainerDao.update(*containers.toTypedArray())
        }
    }
}
