(ns openloop.core
  (require
   ;; [automat.viz :refer (view)]
   ;; [automat.core :as a]
   [reduce-fsm :as fsm]
   ))

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

(swap! fsm-state fsm/fsm-event :loop-btn)
(swap! fsm-state fsm/fsm-event :tap)
(swap! fsm-state fsm/fsm-event :timeout)
(swap! fsm-state fsm/fsm-event :other-loop-btn)
(loop-fsm 0)

(def all-loops [ a-loop ]) ; a list of all loops contributing to the output.
;; a-loop has everything we need to:
;;    - play a loop
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
(def source-audio-array [source-audio-block]) ; a list of mutations to the loop audio buffer
(def source-audio-block ; a block of source audio mapped to a block of buffer audio
  {:length 0 ; how long is the block of audio that we want to replace
   :start-loop 0 ; where does it go in the buffer
   :start-source 0 ; where does it come from
   :xfade-data x-fade; how should we x-fade from the previous audio to the new and back
   })
(def x-fade
  {:xfade-length 10 ;how long should it take to x-fade
   :xfade-offset 0 ;where should the center of the x-fade be, relative to the start position
                                        ; possibly other data, like shape
   })
(def loop-osc-array [loop-osc-block] ) ; a list of mutations
(def loop-osc-block ; a block of source audio mapped to a block of buffer audio
  {:length 0 ; how long is the block of audio that we want to replace
   :start-loop 0 ; where does it go in the buffer
   :osc-sources [0] ; where does it come from, an array of input osc start indexes, each representing a stream of osc data
   :transform nil ; what function did we use to get from the osc sources to the output
   })

;; (defrecord A-Loop [loop-nr prev-sources])
(def loop0 (a-loop))
(:loop-nr loop0)
(:prev-sources loop0)
( loop0)
(assoc)


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
