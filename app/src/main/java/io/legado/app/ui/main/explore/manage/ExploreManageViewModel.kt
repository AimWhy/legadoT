package io.legado.app.ui.main.explore.manage

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.ExploreContainer
import io.legado.app.help.source.ExploreContainerHelp

class ExploreManageViewModel(application: Application) : BaseViewModel(application) {

    fun update(vararg container: ExploreContainer) {
        execute { appDb.exploreContainerDao.update(*container) }
    }

    fun delete(container: ExploreContainer) {
        execute {
            appDb.exploreContainerDao.delete(container)
            ExploreContainerHelp.removeCache(container.id)
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
}
