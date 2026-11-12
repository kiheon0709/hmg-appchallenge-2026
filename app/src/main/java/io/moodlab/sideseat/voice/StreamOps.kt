package io.moodlab.sideseat.voice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * 첫 문장이 [afterMs] 안에 안 오면 [filler]("음, 잠깐만요.")를 먼저 흘려보낸다.
 * 첫 문장이 먼저 오면 [filler]는 나가지 않는다. 순서는 항상 filler → 본문.
 */
fun Flow<String>.withFiller(
    afterMs: Long,
    filler: String,
    onFiller: () -> Unit = {},
    /** true면 채움말을 내지 않는다(예: LLM 글자가 이미 오기 시작해 첫 문장이 곧 나올 때). */
    suppress: () -> Boolean = { false },
): Flow<String> = channelFlow {
    val lock = Mutex()
    var firstClaimed = false
    val fillerJob = launch {
        delay(afterMs)
        lock.withLock {
            if (!firstClaimed && !suppress()) {
                firstClaimed = true
                onFiller()
                send(filler)
            }
        }
    }
    collect { s ->
        lock.withLock {
            firstClaimed = true
            send(s)
        }
        fillerJob.cancel()
    }
    fillerJob.cancel()
}

/**
 * 원본이 첫 문장을 [firstTimeoutMs] 안에 못 내거나, 첫 문장 전에 실패하거나, 아무것도 안 내면
 * [fallback]을 대신 흘려보낸다. 첫 문장을 낸 뒤의 실패는 말을 거기서 멈춘다(이미 말한 것을 되풀이하지 않는다).
 */
fun Flow<String>.withFallback(
    firstTimeoutMs: Long,
    onFailure: (Throwable?) -> Unit = {},
    fallback: suspend () -> List<String>,
): Flow<String> = flow {
    var emittedAny = false
    var failure: Throwable? = null
    try {
        coroutineScope {
            val ch: ReceiveChannel<String> = this@withFallback.produceIn(this)
            try {
                val first = withTimeout(firstTimeoutMs) { ch.receiveCatching() }
                first.getOrNull()?.let { emittedAny = true; emit(it) }
                    ?: run { failure = first.exceptionOrNull() }
                if (emittedAny) {
                    for (s in ch) emit(s)
                }
            } catch (e: TimeoutCancellationException) {
                failure = e
                ch.cancel()
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        failure = e
    }
    if (!emittedAny) {
        onFailure(failure)
        fallback().forEach { emit(it) }
    } else if (failure != null) {
        onFailure(failure)
    }
}
