(ns openloop.core
  (require
   [clojure.spec :as s]
   [clojure.test.check.generators :as gen]
   ;; [automat.viz :refer (view)]
   ;; [automat.core :as a]
   ;; [clojure.core.async :as as]
   [reduce-fsm :as fsm]
   [openloop.constants]))

(defn inc-val [val & _] (inc val))

(fsm/defsm-inc loop-fsm
  [[:idle
    :loop-btn -> :rec-master
    :tap -> {:action inc-val} :start-counting]
   [:rec-master
    :loop-btn ->  :play-master
    :timeout -> :idle]
   [:start-counting
    :tap -> {:action inc-val} :counting
    :loop-btn -> :rec-master
    :timeout -> :idle
    ]
   [:counting
    :tap -> {:action inc-val} :counting
    :loop-btn -> :rec-master
    :timeout -> :rec-master
    ]
   [:play-master
    :loop-btn -> :rec-master
    ]])

(fsm/show-fsm loop-fsm)

(def fsm-state (atom (loop-fsm  0)))
(:value @fsm-state)
(:state @fsm-state)

(defn fsm-step
  ""
  []
  )


(swap! fsm-state fsm/fsm-event :loop-btn)
(swap! fsm-state fsm/fsm-event :tap)
(swap! fsm-state fsm/fsm-event :timeout)
(swap! fsm-state fsm/fsm-event :other-loop-btn)
(loop-fsm 0)

openloop.constants

(s/def ::suit #{:club :diamond :heart :spade})

(s/def ::inputs
  (s/cat
   :loop-btns nil
   :tap nil
   :timeout nil ))

(s/def ::all-loops
  (s/+ (s/cat :a-loop ::loop-type)))

(pprint (s/exercise ::all-loops 2))

(s/def ::loop-type (s/cat
                    :length ::loop-length-type
                    :audio ::loop-audio-type
                    :FX ::FX-type
                    :osc ::loop-osc-type
                    :modification-stack ::modification-stack-type ))

(s/def ::loop-audio-type (s/cat :dry-audio ::audio-type
                                :wet-audio ::audio-type))
;; from https://github.com/candera/dynne
;; Thanks to Rich Hickey for his suggestion to rewrite dynne in terms of sequences of double arrays, which led to a massive increase in performance.

;; max loop length is just a few samples while designing the data structures
(def max-loop-length 8)
(def max-undo-nr 10)
(s/def ::audio-type (s/coll-of ::mono-audio-type :kind vector? :count nr-chan))
(s/def ::mono-audio-type (s/every double? :kind vector? :count max-loop-length ))
;; (s/def ::mono-audio-type double-array?)

(s/def ::FX-type int?)
(s/def ::loop-osc-type int?)
(s/def ::modification-stack-type (s/cat
                                  :audio ::audio-modification-stack-type
                                  :osc ::osc-modification-stack-type))

;; (s/def ::prev-audio-indexes-type (s/with-gen

;; (pprint (gen/sample (s/gen ::prev-audio-indexes-type) 20))

;; (s/def ::audio-modification-stack-type  (s/coll-of ::audio-modification-type, :count (count ), :max-count max-loop-length :distinct true :into #{} ))
;; (s/def ::audio-modification-stack-type  (s/* ::audio-modification-type))

(s/def ::audio-modification-type (s/cat ; a modification to the audio in a loop buffer
                                  :dest-index ::loop-index-type ; where does the new audio go?
                                  :length ::loop-length-type ; how many samples do we replace?
                                  :src-index pos-int?)) ; where does the new audio come from?

;; (s/def ::audio-modification-stack-type  (s/map-of pos-int? ::audio-modification-type))
(s/def ::audio-modification-stack-type (s/coll-of ::audio-modification-type, :max-count max-undo-nr ))

(println "oooooooooooooooooooooooooooo")
(pprint (gen/generate (s/gen ::audio-modification-stack-type  )))
(pprint (s/exercise ::loop-type  2))
(s/def ::int-map (s/map-of keyword? integer?))
(gen/sample (s/gen ::int-map))
(gen/map)

(s/def ::loop-index-type (s/int-in 0 max-loop-length))
;; (s/def ::loop-index-type (s/spec #(s/int-in-range? 0 max-loop-length %)))
(s/def ::loop-length-type (s/int-in 1 (inc  max-loop-length)))

(pprint (s/exercise ::audio-modification-stack-type  max-loop-length))
(s/describe ::audio-modification-stack-type )
(pprint (s/exercise ::loop-length-type 20))
;; (pprint (s/exercise ::prev-audio-indexes-type 40))

(s/def ::osc-modification-stack-type int? )

(pprint (s/exercise ::loop-type 4))



(def email-regex #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$")
(s/def ::email-type (s/and string? #(re-matches email-regex %)))

(s/def ::acctid int?)
(s/def ::first-name string?)
(s/def ::last-name string?)
(s/def ::email ::email-type)

(s/def ::person (s/keys :req [::first-name ::last-name]
                        ))

(pprint (s/exercise ::person 10))


(s/def ::kws (s/with-gen (s/and keyword? #(= (namespace %) "my.domain"))
               #(s/gen ::person)))
(s/valid? ::kws :my.domain/name)  ;; true
(gen/sample (s/gen ::kws))


(let [rows (Integer/parseInt (read-line))
      cols (Integer/parseInt (read-line))
      a (to-array-2d (repeat rows (repeat cols nil)))]
  (aset a 0 0 12)
  (println "Element at 0,0:" (aget a 0 0)))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; define input state
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def inputs
  {:loop-btns (boolean-array nr-loops nil)
   :tap (boolean nil)
   :timeout (boolean nil)
   })
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; define loop data
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def all-loops [ a-loop ]) ; a list of all loops contributing to the output.
;; a-loop has everything we need to:
;;    - play a loop
;;    - change a loop
;;    - reconstruct it from dry signal
;;    - undo / redo
(def a-loop {
             :loop-nr 0 ; which of the nr-loops buffers are we?
             :audio loop-audio ; the actual audio, dry and wet (effected)
             :FX loop-FX ; the effect chain that gets dry to wet
             :osc loop-osc ; the automation for the FX
             :prev-sources prev-sources-array}) ; for each point in the buffer, define where the data came from
(def loop-audio {:dry-audio
                 :wet-audio })
(def loop-FX nil)
(def loop-osc nil)
(def prev-sources-array {:audio source-audio-array :osc loop-osc-array}) ; a list of mutations to the audio and osc
(def source-audio-array [source-audio-block]) ; a chronological vector  of mutations to the loop audio buffer
(def source-audio-block ; a block of source audio mapped to a block of buffer audio
  {
   :start-loop 0 ; where does it go in the buffer
   :length 0 ; how long is the block of audio that we want to replace
   :start-source 0 ; where does it come from
   :xfade-data x-fade; how should we x-fade from the previous audio to the new and back
   })
(def x-fade
  {:xfade-length 10 ;how long should it take to x-fade
   :xfade-offset 0 ;where should the center of the x-fade be, relative to the start position
                                        ; possibly other data, like shape
   })
(def loop-osc-array [loop-osc-block] ) ; a chronological vector of mutations
(def loop-osc-block ; a block of source audio mapped to a block of buffer audio
  {:length 0 ; how long is the block of audio that we want to replace
   :start-loop 0 ; where does it go in the buffer
   :osc-sources [0] ; where does it come from, an array of input osc start indexes, each representing a stream of osc data
   :transform nil ; what function did we use to get from the osc sources to the output
   })

(pprint loop0)
(def loop0 a-loop)
(:loop-nr loop0)
(:audio loop0)
(:audio
 (:prev-sources loop0))
(:osc
 (:prev-sources loop0))


(def PORT 4242)

                                        ; start a server and create a client to talk with it
(def server (osc-server PORT))
(def client (osc-client "localhost" PORT))

                                        ; Register a handler function for the /test OSC address
                                        ; The handler takes a message map with the following keys:
                                        ;   [:src-host, :src-port, :path, :type-tag, :args]
(osc-handle server "/test" (fn [msg] (println "MSG: " msg)))

                                        ; send it some messages
(doseq [val (range 10)]
  (osc-send client "/test" "i" val "some more"))

(Thread/sleep 1000)

                                        ;remove handler
(osc-rm-handler server "/test")

                                        ; stop listening and deallocate resources
(osc-close client)
(osc-close server)


;; maps

(def existingMap {:dog 1 :cat 2 :zebra 991})
(into (sorted-map) existingMap)
;; vectors

;; A vector is similar to an array, in that it’s a 0-indexed collection.
(get ["a" {:name "Pugsley Winterbottom"} "c"] 1)
;; You can create vectors with the vector function:
(vector "creepy" "full" "moon")
;; You can use the conj function to add additional elements to the vector.
;; Elements are added to the end of a vector:
(conj [1 2 3] 4)

;; lists

;; Lists are similar to vectors in that they’re linear collections of values. But there are some differences.
;; You can’t retrieve list elements with get.
;; To write a list literal, just insert the elements into parentheses and use a single quote at the beginning:
'(1 2 3 4)
(list 1 "two" {3 4})
;; Elements are added to the beginning of a list:
(conj '(1 2 3) 4)
;; A good rule of thumb is that if you need to easily add items to the beginning of a sequence or if you’re writing a macro, you should use a list. Otherwise, you should use a vector.

;; Sets

;; Sets are collections of unique values.
#{"kurt vonnegut" 20 :icicle}
(hash-set 1 1 2 2)
                                        ; => #{1 2}
