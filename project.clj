(defproject openloop "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "GNU General Public License, version 2"
            :url "http://www.gnu.org/licenses/gpl-2.0.html"}
  :dependencies [
                 ;; [org.clojure/clojure "1.8.0"]
                 [org.clojure/clojure "1.9.0-alpha10"]
                 ;; [org.clojure/test.check "0.9.0"]
                 ;; [overtone "0.10.1"]
                 [quil "2.4.0"]
                 ;; [automat "0.2.0"]
                 [reduce-fsm "0.1.4"]

                 ;; for overtone:
                 ;; [org.clojure/clojure "1.5.1"]
                 [org.clojure/data.json "0.2.3"]
                 [clj-native "0.9.3"]
                 [overtone/at-at "1.2.0"]
                 [overtone/osc-clj "0.9.0"]
                 [overtone/byte-spec "0.3.1"]
                 [overtone/midi-clj "0.5.0"]
                 [overtone/libs.handlers "0.2.0"]
                 [overtone/scsynth "3.5.7.0"]
                 [overtone/scsynth-extras "3.5.7.0"]
                 [clj-glob "1.0.0"]

                 ]
  :plugins [[lein-git-deps "0.0.1-SNAPSHOT"]]
  :git-dependencies [["https://github.com/overtone/overtone.git"]]
  :source-paths ["src" ".lein-git-deps/overtone/src/"]
  :main ^:skip-aot openloop.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all} :dev {:dependencies [[org.clojure/test.check "0.9.0"]]}})
