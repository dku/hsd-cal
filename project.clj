(defproject hsd-cal "0.1.0-SNAPSHOT"
  :description "create calendar for Hochschuldikatik"
  :url "http://dirk-kutscher.info"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [clj-icalendar "0.1.4"]
                 [hickory "0.7.1"]
                 [clj-http "3.10.0"]
                 ]
                                        ;  :main ^:skip-aot hsd-cal.core
  :main hsd-cal.core
  :aot [hsd-cal.core]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
