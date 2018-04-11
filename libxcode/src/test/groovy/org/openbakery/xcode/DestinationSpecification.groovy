package org.openbakery.xcode

import spock.lang.Specification

class DestinationSpecification extends Specification {

    def "A destination target type should be resolved successfully from it's platform"() {
        expect:
        Type.typeFromString(platform) == target

        where:
        platform            | target
        "iOS Simulator"     | Type.iOS
        "tvOS Simulator"    | Type.tvOS
        "watchOS Simulator" | Type.watchOS
        "invalid"           | null
        ""                  | null
        null                | null
    }
}