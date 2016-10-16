package utils.filters

/**
 * Created by madalien on 18/05/16.
 */
import javax.inject.Inject

import play.api.http.HttpFilters
import play.filters.cors.CORSFilter
import play.filters.gzip.GzipFilter

class FiltersProd @Inject() (
    gzip: GzipFilter,
    corsFilter: CORSFilter
) extends HttpFilters {
  def filters = Seq(corsFilter, gzip)
}

