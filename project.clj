(defproject openloop "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "GNU General Public License, version 2"
            :url "http://www.gnu.org/licenses/gpl-2.0.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [overtone "0.10.1"]
                 [quil "2.4.0"]]
  :main ^:skip-aot openloop.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
