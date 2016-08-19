(ns openloop.core
  (require
   [clojure.spec :as s]
   [clojure.test.check.generators :as gen]
   [openloop.constants]))


(s/def ::inputs
  (s/cat
   :loop-btns nil
   :tap nil
   :timeout nil ))

(s/def ::all-loops
  (s/+ (s/cat :a-loop ::loop-type)))

(println "000000000000000000000000000000000")
(pprint (s/exercise ::all-loops 2))

(s/def ::loop-type (s/cat
                    :length ::loop-length-type
                    :audio ::loop-audio-type
                    :FX ::FX-type
                    :osc ::loop-osc-type
                    :sources ::sources-type ))

(s/def ::loop-audio-type (s/cat :dry-audio ::buffer-nr-type
                                :wet-audio ::buffer-nr-type))
;; from https://github.com/candera/dynne
;; Thanks to Rich Hickey for his suggestion to rewrite dynne in terms of sequences of double arrays, which led to a massive increase in performance.

;; max loop length is just a few samples while designing the data structures
(def max-loop-length 8)
(def max-undo-nr 10)
(s/def ::buffer-nr-type (s/int-in 0 (* 2 nr-loops)))
;; (s/def ::audio-type (s/coll-of ::mono-audio-type :kind vector? :count nr-chan))
;; (s/def ::mono-audio-type (s/every double? :kind vector? :count max-loop-length ))
;; (s/def ::mono-audio-type double-array?)

(s/def ::FX-type int?)
(s/def ::loop-osc-type int?) ; placeholder for the osc data of a loop
(s/def ::sources-type (s/cat
                       :audio ::audio-sources-type
                       :osc ::osc-sources-type))

;; (s/def ::prev-audio-indexes-type (s/with-gen

;; (pprint (gen/sample (s/gen ::prev-audio-indexes-type) 20))

;; (s/def ::audio-sources-type   (s/coll-of ::audio-block-type, :count (count ), :max-count max-loop-length :distinct true :into #{} ))
;; (s/def ::audio-sources-type   (s/* ::audio-block-type))

;; maybe needed for performance, so it's easier to know which nodes to replace or change on updates.
(s/def ::audio-dest-indexes-type (s/coll-of ::loop-index-type, :min-count 0, :max-count max-loop-length :distinct true :into #{} ))

(s/def ::audio-block-type (s/cat ; a continuous block of audio in a loop buffer
                           ;; :dest-index ::loop-index-type ; where does the new audio go?  ; superfluous, since we store this object in a map with dest-indexes as keys
                           ;; :length ::loop-length-type ; how many samples do we replace?  ; can be deduced from the value of the next dst-index
                           :src-index pos-int? ; where does the new audio come from?
                                        ; to be precise: which sample in the src corresponds to the 0'th sample in the buffer
                           :x-fade-data ::x-fade-data-type ; how do we crossfade from one to the next?
                           ))

(s/def ::x-fade-data-type int?)

;; (s/def ::audio-sources-type   (s/map-of pos-int? ::audio-block-type))
;; (s/def ::audio-sources-type  (s/coll-of ::audio-block-type, :max-count max-undo-nr ))
(s/def ::audio-sources-type  (s/map-of ::loop-index-type ::audio-block-type, :max-count max-loop-length ))


(println "oooooooooooooooooooooooooooo")
(pprint (gen/generate (s/gen ::audio-sources-type   )))
(pprint (s/exercise ::loop-type  2))
(s/def ::int-map (s/map-of keyword? integer?))
(gen/sample (s/gen ::int-map))
(gen/map)

(s/def ::loop-index-type (s/int-in 0 max-loop-length))
;; (s/def ::loop-index-type (s/spec #(s/int-in-range? 0 max-loop-length %)))
(s/def ::loop-length-type (s/int-in 1 (inc  max-loop-length)))

(pprint (s/exercise ::audio-sources-type   max-loop-length))
(s/describe ::audio-sources-type  )
(pprint (s/exercise ::loop-length-type 20))
;; (pprint (s/exercise ::prev-audio-indexes-type 40))

(s/def ::osc-sources-type int? )

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

(s/def ::suit #{:club :diamond :heart :spade})
