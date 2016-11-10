/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.process.internal.daemon

import org.gradle.process.internal.daemon.health.memory.MemoryInfo
import spock.lang.Specification
import spock.lang.Unroll

class WorkerDaemonSimpleMemoryExpirationTest extends Specification {

    def workingDir = new File("some-dir")
    def oneGbOptions = new DaemonForkOptions('1g', '1g', ['one-gb-options'])
    def twoGbOptions = new DaemonForkOptions('2g', '2g', ['two-gb-options'])
    def threeGbOptions = new DaemonForkOptions('3g', '3g', ['three-gb-options'])
    def fourGbOptions = new DaemonForkOptions('4g', '4g', ['four-gb-options'])
    def twelveGbOptions = new DaemonForkOptions('12g', '12g', ['twelve-gb-options'])
    def memoryInfo = Mock(MemoryInfo) { getTotalPhysicalMemory() >> gb(8) }
    def expiration = new WorkerDaemonSimpleMemoryExpiration(memoryInfo, 0.05)

    def "does not expire worker daemons when enough system memory available"() {
        given:
        def client1 = new WorkerDaemonClient(twoGbOptions, Stub(WorkerDaemonWorker))
        def client2 = new WorkerDaemonClient(twoGbOptions, Stub(WorkerDaemonWorker))
        def allClients = [client1, client2]
        def idleClients = allClients.collect()

        when:
        expiration.eventuallyExpireDaemons(twoGbOptions, idleClients, allClients)

        then:
        1 * memoryInfo.getFreePhysicalMemory() >> gb(3)

        and:
        idleClients == [client1, client2]
        allClients == [client1, client2]
    }

    def "expires least recently used idle worker daemon to free system memory when requesting a new one"() {
        given:
        def worker1 = Mock(WorkerDaemonWorker)
        def worker2 = Mock(WorkerDaemonWorker)
        def client1 = new WorkerDaemonClient(oneGbOptions, worker1)
        def client2 = new WorkerDaemonClient(twoGbOptions, worker2)
        def allClients = [client1, client2]
        def idleClients = allClients.collect()

        when:
        expiration.eventuallyExpireDaemons(twoGbOptions, idleClients, allClients)

        then:
        1 * memoryInfo.getFreePhysicalMemory() >> gb(2)
        1 * worker1.stop()
        0 * worker2.stop()

        and:
        idleClients == [client2]
        allClients == [client2]
    }

    def "expires enough idle worker daemons to fit requested one in system memory"() {
        given:
        def worker1 = Mock(WorkerDaemonWorker)
        def worker2 = Mock(WorkerDaemonWorker)
        def worker3 = Mock(WorkerDaemonWorker)
        def client1 = new WorkerDaemonClient(threeGbOptions, worker1)
        def client2 = new WorkerDaemonClient(twoGbOptions, worker2)
        def client3 = new WorkerDaemonClient(oneGbOptions, worker3)
        def allClients = [client1, client2, client3]
        def idleClients = allClients.collect()

        when:
        expiration.eventuallyExpireDaemons(fourGbOptions, idleClients, allClients)

        then:
        1 * memoryInfo.getFreePhysicalMemory() >> gb(1)
        1 * worker1.stop()
        1 * worker2.stop()
        0 * worker3.stop()

        then:
        idleClients == [client3]
        allClients == [client3]
    }

    def "expires all idle daemons to fit requested one in system memory"() {
        given:
        def worker1 = Mock(WorkerDaemonWorker)
        def worker2 = Mock(WorkerDaemonWorker)
        def client1 = new WorkerDaemonClient(oneGbOptions, worker1)
        def client2 = new WorkerDaemonClient(threeGbOptions, worker2)
        def allClients = [client1, client2]
        def idleClients = allClients.collect()

        when:
        expiration.eventuallyExpireDaemons(fourGbOptions, idleClients, allClients)

        then:
        1 * memoryInfo.getFreePhysicalMemory() >> gb(1)
        1 * worker1.stop()
        1 * worker2.stop()

        then:
        idleClients == []
        allClients == []
    }

    def "expires all idle worker daemons when requested one require more than total system memory"() {
        def worker1 = Mock(WorkerDaemonWorker)
        def worker2 = Mock(WorkerDaemonWorker)
        def worker3 = Mock(WorkerDaemonWorker)
        def client1 = new WorkerDaemonClient(oneGbOptions, worker1)
        def client2 = new WorkerDaemonClient(twoGbOptions, worker2)
        def client3 = new WorkerDaemonClient(threeGbOptions, worker3)
        def allClients = [client1, client2, client3]
        def idleClients = allClients.collect()

        when:
        expiration.eventuallyExpireDaemons(twelveGbOptions, idleClients, allClients)

        then:
        1 * memoryInfo.getFreePhysicalMemory() >> gb(1)
        1 * worker1.stop()
        1 * worker2.stop()
        1 * worker3.stop()

        then:
        idleClients == []
        allClients == []
    }

    def "expire all compatible worker daemons but the more recently used when trying to fit requested one in system memory"() {
        given:
        def worker1 = Mock(WorkerDaemonWorker)
        def worker2 = Mock(WorkerDaemonWorker)
        def worker3 = Mock(WorkerDaemonWorker)
        def worker4 = Mock(WorkerDaemonWorker)
        def client1 = new WorkerDaemonClient(oneGbOptions, worker1)
        def client2 = new WorkerDaemonClient(oneGbOptions, worker2)
        def client3 = new WorkerDaemonClient(oneGbOptions, worker3)
        def client4 = new WorkerDaemonClient(oneGbOptions, worker4)
        def allClients = [client1, client2, client3, client4]
        def idleClients = allClients.collect()

        when:
        expiration.eventuallyExpireDaemons(twoGbOptions, idleClients, allClients)

        then:
        1 * memoryInfo.getFreePhysicalMemory() >> gb(1)
        1 * memoryInfo.getFreePhysicalMemory() >> gb(4)
        1 * worker1.stop()
        1 * worker2.stop()
        1 * worker3.stop()
        0 * worker4.stop()

        then:
        idleClients == [client4]
        allClients == [client4]
    }

    def "can parse maximum heap null"() {
        expect:
        WorkerDaemonSimpleMemoryExpiration.parseHeapSize(memoryInfo, null) == WorkerDaemonSimpleMemoryExpiration.getDefaultMaxHeap(memoryInfo)
    }

    @Unroll
    def "can parse maximum heap '#maxHeap'"() {
        given:
        WorkerDaemonSimpleMemoryExpiration.parseHeapSize(memoryInfo, maxHeap) == maxHeapBytes

        where:
        maxHeap | maxHeapBytes
        ''      | WorkerDaemonSimpleMemoryExpiration.getDefaultMaxHeap(Stub(MemoryInfo))
        '512m'  | mb(512)
        '768M'  | mb(768)
        '64g'   | gb(64)
        '4G'    | gb(4)
    }

    private static long gb(long gb) {
        gb * 1024 * 1024 * 1024
    }

    private static long mb(long mb) {
        mb * 1024 * 1024
    }
}
