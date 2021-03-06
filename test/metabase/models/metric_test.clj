(ns metabase.models.metric-test
  (:require [expectations :refer :all]
            (metabase.models [database :refer [Database]]
                             [hydrate :refer :all]
                             [metric :refer :all, :as metric]
                             [table :refer [Table]])
            [metabase.test.data :refer :all]
            [metabase.test.data.users :refer :all]
            [metabase.test.util :as tu]
            [metabase.util :as u]))

(defn- user-details
  [username]
  (dissoc (fetch-user username) :date_joined :last_login))

(defn- metric-details
  [{:keys [creator] :as metric}]
  (-> (dissoc metric :id :table_id :created_at :updated_at)
      (update :creator (u/rpartial dissoc :date_joined :last_login))))

(defn- create-metric-then-select!
  [table name description creator definition]
  (metric-details (create-metric! table name description creator definition)))

(defn- update-metric-then-select!
  [metric]
  (metric-details (update-metric! metric (user->id :crowberto))))


;; create-metric!
(expect
  {:creator_id              (user->id :rasta)
   :creator                 (user-details :rasta)
   :name                    "I only want *these* things"
   :description             nil
   :how_is_this_calculated  nil
   :show_in_getting_started false
   :caveats                 nil
   :points_of_interest      nil
   :is_active               true
   :definition              {:clause ["a" "b"]}}
  (tu/with-temp* [Database [{database-id :id}]
                  Table    [{:keys [id]}      {:db_id database-id}]]
    (create-metric-then-select! id "I only want *these* things" nil (user->id :rasta) {:clause ["a" "b"]})))


;; exists?
(expect
  [true
   false]
  (tu/with-temp* [Database [{database-id :id}]
                  Table    [{table-id :id}    {:db_id database-id}]
                  Metric   [{metric-id :id}   {:table_id   table-id
                                               :definition {:database 45
                                                            :query    {:filter ["yay"]}}}]]
    [(metric/exists? metric-id)
     (metric/exists? Integer/MAX_VALUE)])) ; a Metric that definitely doesn't exist


;; retrieve-metric
(expect
  {:creator_id              (user->id :rasta)
   :creator                 (user-details :rasta)
   :name                    "Toucans in the rainforest"
   :description             "Lookin' for a blueberry"
   :how_is_this_calculated  nil
   :show_in_getting_started false
   :caveats                 nil
   :points_of_interest      nil
   :is_active               true
   :definition              {:database 45
                             :query    {:filter ["yay"]}}}
  (tu/with-temp* [Database [{database-id :id}]
                  Table    [{table-id :id}    {:db_id database-id}]
                  Metric   [{metric-id :id}   {:table_id    table-id
                                               :definition  {:database 45
                                                             :query    {:filter ["yay"]}}}]]
    (let [{:keys [creator] :as metric} (retrieve-metric metric-id)]
      (update (dissoc metric :id :table_id :created_at :updated_at)
              :creator (u/rpartial dissoc :date_joined :last_login)))))


;; retrieve-segements
(expect
  [{:creator_id              (user->id :rasta)
    :creator                 (user-details :rasta)
    :name                    "Metric 1"
    :description             nil
    :how_is_this_calculated  nil
    :show_in_getting_started false
    :caveats                 nil
    :points_of_interest      nil
    :is_active               true
    :definition              {}}]
  (tu/with-temp* [Database [{database-id :id}]
                  Table    [{table-id-1 :id}    {:db_id database-id}]
                  Table    [{table-id-2 :id}    {:db_id database-id}]
                  Metric   [{segement-id-1 :id} {:table_id table-id-1, :name "Metric 1", :description nil}]
                  Metric   [{metric-id-2 :id}   {:table_id table-id-2}]
                  Metric   [{metric-id3 :id}    {:table_id table-id-1, :is_active false}]]
    (doall (for [metric (u/prog1 (retrieve-metrics table-id-1)
                                 (assert (= 1 (count <>))))]
             (update (dissoc (into {} metric) :id :table_id :created_at :updated_at)
                     :creator (u/rpartial dissoc :date_joined :last_login))))))


;; update-metric!
;; basic update.  we are testing several things here
;;  1. ability to update the Metric name
;;  2. creator_id cannot be changed
;;  3. ability to set description, including to nil
;;  4. ability to modify the definition json
;;  5. revision is captured along with our commit message
(expect
  {:creator_id              (user->id :rasta)
   :creator                 (user-details :rasta)
   :name                    "Costa Rica"
   :description             nil
   :how_is_this_calculated  nil
   :show_in_getting_started false
   :caveats                 nil
   :points_of_interest      nil
   :is_active               true
   :definition              {:database 2
                             :query    {:filter ["not" "the toucans you're looking for"]}}}
  (tu/with-temp* [Database [{database-id :id}]
                  Table  [{table-id :id}  {:db_id database-id}]
                  Metric [{metric-id :id} {:table_id table-id}]]
    (update-metric-then-select! {:id                      metric-id
                                 :name                    "Costa Rica"
                                 :description             nil
                                 :how_is_this_calculated  nil
                                 :show_in_getting_started false
                                 :caveats                 nil
                                 :points_of_interest      nil
                                 :creator_id              (user->id :crowberto)
                                 :table_id                456
                                 :definition              {:database 2
                                                           :query    {:filter ["not" "the toucans you're looking for"]}}
                                 :revision_message        "Just horsing around"})))

;; delete-metric!
(expect
  {:creator_id              (user->id :rasta)
   :creator                 (user-details :rasta)
   :name                    "Toucans in the rainforest"
   :description             "Lookin' for a blueberry"
   :how_is_this_calculated  nil
   :show_in_getting_started false
   :caveats                 nil
   :points_of_interest      nil
   :is_active               false
   :definition              {}}
  (tu/with-temp* [Database [{database-id :id}]
                  Table    [{table-id :id}  {:db_id database-id}]
                  Metric   [{metric-id :id} {:table_id table-id}]]
    (delete-metric! metric-id (user->id :crowberto) "revision message")
    (metric-details (retrieve-metric metric-id))))


;; ## Metric Revisions

(tu/resolve-private-vars metabase.models.metric serialize-metric diff-metrics)

;; serialize-metric
(expect
  {:id                      true
   :table_id                true
   :creator_id              (user->id :rasta)
   :name                    "Toucans in the rainforest"
   :description             "Lookin' for a blueberry"
   :how_is_this_calculated  nil
   :show_in_getting_started false
   :caveats                 nil
   :points_of_interest      nil
   :definition              {:aggregation ["count"]
                             :filter      ["AND" [">" 4 "2014-10-19"]]}
   :is_active               true}
  (tu/with-temp* [Database [{database-id :id}]
                  Table    [{table-id :id} {:db_id database-id}]
                  Metric   [metric         {:table_id   table-id
                                            :definition {:aggregation ["count"]
                                                         :filter      ["AND" [">" 4 "2014-10-19"]]}}]]
    (-> (serialize-metric Metric (:id metric) metric)
        (update :id boolean)
        (update :table_id boolean))))

;; diff-metrics

(expect
  {:definition  {:before {:filter ["AND" [">" 4 "2014-10-19"]]}
                 :after  {:filter ["AND" ["BETWEEN" 4 "2014-07-01" "2014-10-19"]]}}
   :description {:before "Lookin' for a blueberry"
                 :after  "BBB"}
   :name        {:before "Toucans in the rainforest"
                 :after  "Something else"}}
  (tu/with-temp* [Database [{database-id :id}]
                  Table    [{table-id :id} {:db_id database-id}]
                  Metric   [metric         {:table_id   table-id
                                            :definition {:filter ["AND" [">" 4 "2014-10-19"]]}}]]
    (diff-metrics Metric metric (assoc metric
                                       :name        "Something else"
                                       :description "BBB"
                                       :definition  {:filter ["AND" ["BETWEEN" 4 "2014-07-01" "2014-10-19"]]}))))

;; test case where definition doesn't change
(expect
  {:name {:before "A"
          :after  "B"}}
  (diff-metrics Metric
                {:name        "A"
                 :description "Unchanged"
                 :definition  {:filter ["AND" [">" 4 "2014-10-19"]]}}
                {:name        "B"
                 :description "Unchanged"
                 :definition  {:filter ["AND" [">" 4 "2014-10-19"]]}}))

;; first version, so comparing against nil
(expect
  {:name        {:after  "A"}
   :description {:after "Unchanged"}
   :definition  {:after {:filter ["AND" [">" 4 "2014-10-19"]]}}}
  (diff-metrics Metric
                nil
                {:name        "A"
                 :description "Unchanged"
                 :definition  {:filter ["AND" [">" 4 "2014-10-19"]]}}))

;; removals only
(expect
  {:definition  {:before {:filter ["AND" [">" 4 "2014-10-19"] ["=" 5 "yes"]]}
                 :after  {:filter ["AND" [">" 4 "2014-10-19"]]}}}
  (diff-metrics Metric
                {:name        "A"
                 :description "Unchanged"
                 :definition  {:filter ["AND" [">" 4 "2014-10-19"] ["=" 5 "yes"]]}}
                {:name        "A"
                 :description "Unchanged"
                 :definition  {:filter ["AND" [">" 4 "2014-10-19"]]}}))



;; ## Metric Dependencies

(expect
  {:Segment #{2 3}}
  (metric-dependencies Metric 12 {:definition {:aggregation ["rows"]
                                               :breakout    [4 5]
                                               :filter      ["AND" [">" 4 "2014-10-19"] ["=" 5 "yes"] ["SEGMENT" 2] ["SEGMENT" 3]]}}))

(expect
  {:Segment #{1}}
  (metric-dependencies Metric 12 {:definition {:aggregation ["METRIC" 7]
                                               :filter      ["AND" [">" 4 "2014-10-19"] ["=" 5 "yes"] ["OR" ["SEGMENT" 1] ["!=" 5 "5"]]]}}))

(expect
  {:Segment nil}
  (metric-dependencies Metric 12 {:definition {:aggregation nil
                                               :filter      nil}}))
