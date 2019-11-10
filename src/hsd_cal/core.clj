(ns hsd-cal.core
  (:gen-class)
  (:use [hickory.core])
  (:require [hickory.select :as s])
  (:require [hickory.zip :as z])
  (:require [clj-http.client :as client])
  (:require [clojure.string :as string])
  (:require [clj-icalendar.core :as cal])
;  (:require [clojure.zip :as zip])
  (:import (java.util Date TimeZone))
  (:import (java.text DateFormat SimpleDateFormat))

  )


(def nb-site "https://www.hs-emden-leer.de/einrichtungen/campusdidaktik/neuberufenenprogramm/")

(defn trs []
  "get all table row entries without the headings"
  (let[nb-program (-> (client/get nb-site) :body parse as-hickory)
      nb-page (z/hickory-zip nb-program)]
    (s/select (s/child
               (s/tag :tbody)
               (s/and
                (s/not (s/has-descendant (s/find-in-text #"TEAMCOACHING - alle Jahrgänge")))
                (s/not (s/has-descendant (s/find-in-text #"Angebote im Rahmen des Neuberufenenprogramms")))
                (s/not (s/has-descendant (s/find-in-text #"Weiteres")))
                (s/tag :tr)))
              nb-program)))

(defn getTd [tr]
  "select the TD elements"
  (s/select (s/child (s/tag :td))
            tr))

(defn isEmpty? [element]
  (= element nil))

(defn hasContent? [element]
  (:content element))

(defn scontent [element]
  "get only the string content from a nested element structure and concatenate it"
  (cond
    (isEmpty? element) ""
    (string? element) element
    (hasContent? element) (apply str (map #(scontent %) (:content element)))
    ))


(defn calVal [row idx]
  (scontent (get row idx)))


(defn termin [row]
  (hash-map :date (calVal row 0)
            :name (calVal row 1)
            :instructor (calVal row 2)
            :time (calVal row 3)
            :location (calVal row 4)
            :room (calVal row 5)
            :category (calVal row 6)))
                                    
(defn parseDate [d]
  (let [[day month year] (map string/trim (string/split d #"\."))]
    [day month year]))

(defn dateToString [d m y]
  (str d "." m "." y))

(defn secondWhenNil [n1 n2]
  "return n2 if n1 is nil or empty, otherwise n1"
  (if (or (nil? n1) (= "" n1)) n2 n1))

(defn fixDate [lastDate otherDate]
  (let [[dayL monthL yearL] (parseDate lastDate)
        [dayO monthO yearO] (parseDate otherDate)]
    (dateToString dayO (secondWhenNil monthO monthL) (secondWhenNil yearO yearL))))

(defn fixDates [dates]
  (let [lastDate (last dates)
        otherDates (map (partial fixDate lastDate) (take (- (count dates) 1) dates))]
    (conj (vec otherDates) (apply dateToString (parseDate lastDate)))))
        
(defn setDate [entry date time]
  (assoc (assoc entry :date date) :time time)
  )

(defn checkTermin [entry]
  "check whether date has two date entries -- create vectors of termin"
  (let [dates (string/split (:date entry) #"&")
        allDates (fixDates dates)
        times (string/split (:time entry) #"&")
        ]
    (map (partial setDate entry) allDates times)
   ))


(defn valid? [termin]
  (and
   (:name termin)
   (not (string/includes? (:date termin) "vereinbarung"))))


(defn mkTermin [td]
  "create data from HTML table row"
  (termin (getTd td)))


(defn termine []
  "fetch web data and return list of parsed dates"
  (flatten (map checkTermin (filter valid? (map mkTermin (trs))))))
  

(defn cleanDate [date]
  "remove everything except for digits and dots"
  (apply str (filter #(or (Character/isDigit %) (= \. %)) date))
  )

(defn mkDate [date time]
  "create Date object"
  (let [df (SimpleDateFormat. "dd.MM.yyyy HH.mm zzz")]
    (. df parse (str (cleanDate date) " " (cleanDate time) " CET"))))
    
(defn startEnd [timing]
  "create vector of start and end time"
  (string/split timing #"-")
  )
  

(defn mkCal [events]
  (let [cal (cal/create-cal "HSEL" "Neuberufenenprogramm" "V0.1" "EN")
        _ (reduce (fn [cal event] (cal/add-event! cal event)) cal events)]
    cal
  ))


(defn create-event [termin]
  (let [[startT endT] (startEnd (:time termin))]
    (cal/create-event
     (mkDate (:date termin) startT)
     (mkDate (:date termin) endT)
     (:name termin)
     :unique-id (str (hash termin))
     :description (str "Dozent: " (:instructor termin) ", Kategorie: " (:category termin))
     :location (str (:location termin) ", " (:room termin))
     :organizer nb-site)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (print (cal/output-calendar (mkCal (map create-event (termine)))))
  (flush)
  )

