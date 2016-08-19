(ns openloop.core
  (require
   [clojure.spec :as s]
   [clojure.test.check.generators :as gen]
   [clojure.spec.gen :as sgen]
   [openloop.constants]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; define input state
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::inputs
  (s/cat
   :loop-btns nil
   :tap nil
   :timeout nil ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; define loop data
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::all-loops-type  ; a vector of all loops contributing to the output.
  ;; (s/+ (s/cat :a-loop ::loop-type)))
  (s/coll-of ::loop-type :count nr-loops))

(println "000000000000000000000000000000000")
(pprint (drop 20 (s/exercise ::loop-type 21)))

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
(pprint (s/exercise ::FX-type 4))


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



;; makes them distinct over all content, so index is not necessarily distinct:
;; (s/def ::audio-sources-type   (s/coll-of ::audio-block-type, :count (count ), :max-count max-loop-length :distinct true :into #{} ))
;; (s/def ::audio-sources-type   (s/* ::audio-block-type))


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
(s/def ::audio-sources-type  (s/map-of ::loop-index-type ::audio-block-type, :max-count max-sources-nr ))


(println "oooooooooooooooooooooooooooo")
;; (pprint (gen/generate (s/gen ::audio-sources-type   )))
(pprint (s/exercise ::all-loops-type 4))


(s/def ::loop-index-type (s/int-in 0 max-loop-length))
;; (s/def ::loop-index-type (s/spec #(s/int-in-range? 0 max-loop-length %)))
(s/def ::loop-length-type (s/int-in 1 (inc  max-loop-length)))

(s/def ::osc-sources-type (s/cat
                           :initial-values ::osc-params-type ; a dump of all parameters and values at their initial state
                                        ; optional vector of additional OSC input values or streams, used to calculte the output OSC stream
                           :sources-vector (s/coll-of ::osc-source-type, :max-count max-sources-nr)
                                        ; optional vector of functions that combines the above sources into the output
                           :functions-vector (s/coll-of ::osc-function-type, :max-count max-sources-nr)
                           ))

(s/def ::osc-params-type pos-int?) ; placeholder
(s/def ::osc-source-type pos-int?) ; placeholder
(s/def ::osc-function-type pos-int?) ; placeholder


(s/def ::osc-function-type (s/and keyword? #(= (namespace %) "openloop.osc_functions")))
(s/valid? ::osc-function-type :my.domain/name) ;; true
(gen/sample (s/gen ::osc-function-type)) ;; unlikely we'll generate useful keywords this way

(def osc-function-gen (sgen/fmap #(symbol "openloop.osc_functions" %)
                                 (sgen/such-that #(not= % "")
                                                 (sgen/string-alphanumeric))))
(gen/sample osc-function-gen 5)


;; (s/map-of ::loop-index-type ::osc-block-type, :max-count max-sources-nr ))

;; (pprint (s/exercise ::loop-type 4))



;; (def email-regex #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$")
;; (s/def ::email-type (s/and string? #(re-matches email-regex %)))

;; (s/def ::acctid int?)
;; (s/def ::first-name string?)
;; (s/def ::last-name string?)
;; (s/def ::email ::email-type)

;; (s/def ::person (s/keys :req [::first-name ::last-name]
;;                         ))

;; (pprint (s/exercise ::person 10))


;; (s/def ::kws (s/with-gen (s/and keyword? #(= (namespace %) "my.domain"))
;;                #(s/gen ::person)))
;; (s/valid? ::kws :my.domain/name)  ;; true
;; (gen/sample (s/gen ::kws))


;; (let [rows (Integer/parseInt (read-line))
;;       cols (Integer/parseInt (read-line))
;;       a (to-array-2d (repeat rows (repeat cols nil)))]
;;   (aset a 0 0 12)
;;   (println "Element at 0,0:" (aget a 0 0)))

;; (s/def ::suit #{:club :diamond :heart :spade})
