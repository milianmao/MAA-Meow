package com.aliothmoon.maameow.koin

import com.aliothmoon.maameow.domain.usecase.AnalyzeTaskChainUseCase
import com.aliothmoon.maameow.domain.usecase.PrepareTaskStartUseCase
import com.aliothmoon.maameow.manager.RemoteServiceManager
import org.koin.dsl.module
import timber.log.Timber


val useCaseModule = module {
    factory {
        AnalyzeTaskChainUseCase(get())
    }
    factory {
        PrepareTaskStartUseCase(
            analyzeTaskChainUseCase = get(),
            appAliveChecker = get(),
            appSettings = get(),
            isPackageInstalled = { packageName ->
                try {
                    RemoteServiceManager.getInstanceOrNull()
                        ?.isPackageInstalled(packageName) ?: true
                } catch (e: Exception) {
                    Timber.w(e, "isPackageInstalled check failed for %s", packageName)
                    true
                }
            },
        )
    }
}
