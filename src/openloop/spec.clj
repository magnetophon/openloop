(ns openloop.core
  (require
   [clojure.spec :as s]
   [clojure.test.check.generators :as gen]
   [clojure.spec.gen :as sgen]
   [clojure.spec.test :as stest]
   [openloop.constants]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; spec  input state
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::inputs
  (s/cat
   :loop-btn-downs nil
   :tap nil
   :timeout nil ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; spec  loop data
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; a-loop has everything we need to:
;;    - play a loop
;;    - change a loop
;;    - reconstruct it from dry signal
(s/def ::loop-type (s/cat
                    ;; :loop-nr ::buffer-nr-type ; which of the nr-loops buffers are we?    redundant, cause we store this in
                    :length ::loop-length-type
                    :sources ::sources-type  ; for each point in the buffer, define where the data came from
                    ;; we don't keep audio data in our state, it will be managed by scsynth
                    ;; :audio ::loop-audio-type ; the actual audio, dry and wet (effected)
                    :FX ::FX-type ; the effect chain that gets dry to wet
                    ;; :osc ::loop-osc-type ; the automation for the FX ; will be derived from the sources
                    ))

;; (s/def ::loop-audio-type (s/cat :dry-audio ::buffer-nr-type
;;                                 :wet-audio ::buffer-nr-type))
;; (s/def ::buffer-nr-type (s/int-in 0 (* 2 nr-loops)))
;; (s/def ::loop-audio-type (s/cat :dry-audio ::audio-type ; the source audio, needed to construct:
;;                                 :wet-audio ::audio-type)) ; the effected audio
;; todo: exclude NAN and Infinity.
;; todo: see below
;; from https://github.com/candera/dynne
;; Thanks to Rich Hickey for his suggestion to rewrite dynne in terms of sequences of double arrays, which led to a massive increase in performance.
;; (s/def ::mono-audio-type double-array?) ; doesn't work
;; (s/def ::audio-type (s/coll-of ::mono-audio-type :count nr-chan))
;; (s/def ::mono-audio-type (s/every double? :count max-loop-length ))
(println "000000000000000000000000000000000")
;; (pprint (s/exercise ::FX-type 4))


(s/def ::FX-type (s/cat; the FX chain needed to create the wet audio
                  :internal pos-int? ; scsynth internal FX
                  :external (s/cat
                             :jack-name string? ; the jack name to check if it's running
                             :command string? ; the cli command that runs the FX
                             :port pos-int? ; the OSC port to send automation to
                             )))
;; (s/def ::loop-osc-type pos-int?) ; placeholder for the osc data of a loop
(s/def ::sources-type (s/cat
                       :audio ::audio-sources-type ; where did the dry audio come from
                       :osc ::osc-sources-type)) ; which combination of OSC input streams and calculations lead to the current automation of this loop

(s/def ::audio-block-type (s/cat  ; a block of source audio mapped to a block of buffer audio
                           ;; :dest-index ::loop-index-type ; where does the new audio go?  ; superfluous, since we store this object in a map with dest-indexes as keys
                           ;; :length ::loop-length-type ; how many samples do we replace?  ; can be deduced from the value of the next dst-index
                           :src-index pos-int? ; where does the new audio come from?
                                        ; to be precise: which sample in the src corresponds to the 0'th sample in the buffer
                           :x-fade-data ::x-fade-data-type ; how do we crossfade from one to the next?
                           ))

(s/def ::x-fade-data-type (s/cat
                           :length (s/int-in 0 max-x-fade-length))) ; for now, just indicate how long the crossfade is in ms
;; maybe needed for performance, so it's easier to know which nodes to replace or change on updates.
;; (s/def ::audio-dest-indexes-type (s/coll-of ::loop-index-type, :min-count 0, :max-count max-loop-length :distinct true :into #{} ))

;; the keys represent the start index in the loop where the block should go.
(s/def ::audio-sources-type
  (s/with-gen
    (s/map-of ::loop-index-type ::audio-block-type :max-count max-sources-nr )
    #(gen/fmap (fn [m]
                 (into (sorted-map) m))
               (gen/map (s/gen ::loop-index-type ) (s/gen ::audio-block-type ))))
  )

(s/def ::loop-index-type (s/int-in 0 max-loop-length))
;; (s/def ::loop-index-type (s/spec #(s/int-in-range? 0 max-loop-length %)))
(s/def ::loop-length-type (s/int-in 1 (inc  max-loop-length)))

(s/def ::osc-sources-type
  (s/cat
   :initial-values ::osc-params-type ; a dump of all parameters and values at their initial state
                                        ; optional vector of additional OSC input values or streams, used to calculte the output OSC stream
   ;; :sources-vector (s/coll-of ::osc-source-type, :max-count max-sources-nr)
                                        ; for now, we limit the nr of sources, since we're not using it yet
   :sources-vector (s/coll-of ::osc-source-type, :max-count 2)
                                        ; optional function that combines the above sources into the output
   :function ::osc-function-type
   ))

(s/def ::osc-params-type pos-int?) ; placeholder
(s/def ::osc-source-type pos-int?) ; placeholder

(s/def ::osc-function-type (s/with-gen (s/and keyword? #(= (namespace %) "openloop.osc_functions"))
                             #(s/gen #{:openloop.osc_functions/sum :openloop.osc_functions/example :openloop.osc_functions/filter})))


;; (s/def ::osc-function-type (s/with-gen (s/and keyword? #(= (namespace %) "openloop.osc_functions"))
;;                              (s/gen  osc-function-gen)))
;; (pprint (s/exercise ::all-loops-type 1))


;; (s/def ::osc-function-type (s/with-gen (s/and keyword? #(= (namespace %) "openloop.osc_functions"))
;;                              #(sgen/fmap (fn [s1] (symbol "openloop.osc_functions" s1))
;;                                          (sgen/such-that #(not= s1 "")
;;                                                          (sgen/string-alphanumeric)))))

(def osc-function-gen (sgen/fmap #(symbol "openloop.osc_functions" %)
                                 (sgen/such-that #(not= % "")
                                                 (sgen/string-alphanumeric))))
;; (gen/sample osc-function-gen 5)


(s/def ::all-loops-type  ; a vector of all loops contributing to the output.
  (s/coll-of ::loop-type :count nr-loops))

(s/def ::looper-state-type ; the overall state of the program
  (s/cat
   :booted boolean? ; is scsynth booted
   :connected boolean? ; is scsynth connected
                                        ; we don't have a master loop, just the length (in samples) of what would be the master in a traditional looper
   :master-length pos-int? ; sync loop length in samples
   :master-offset int? ; if we redefine the start of the loop afterwards: how many samples before or after the old start.
   :all-loops ::all-loops-type))

(println "000000000000000000000000000000000")
(pprint (drop 90 (s/exercise ::looper-state-type 91)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; spec functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(pprint (s/describe ::loop-type))
;; (defn ranged-rand
;;   "Returns random int in range start <= rand < end"
;;   [start end]
;;   (+ start (long (rand (- start end)))))

;; (s/fdef ranged-rand
;;         :args (s/and (s/cat :start int? :end int?)
;;                      #(< (:start %) (:end %)))
;;         :ret int?
;;         :fn (s/and #(>= (:ret %) (-> % :args :start))
;;                    #(< (:ret %) (-> % :args :end))))

;; (long (rand))

;; (ranged-rand 3 77)

;; (stest/instrument `ranged-rand)
;; (ranged-rand 8 5)

;; (pprint (stest/check `ranged-rand))

;; (pprint (stest/abbrev-result (first (stest/check `ranged-rand))))

;; (stest/enumerate-namespace 'openloop.core)
